package io.github.open55.otx;

import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.dto.response.LedgerEntryResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.application.withdraw.dto.request.WithdrawRequestDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawBroadcastResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawSettleResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawStatusResponseDTO;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.BizIdempotentException;
import io.github.open55.otx.domain.chain.port.BroadcastResult;
import io.github.open55.otx.domain.chain.port.SignRequest;
import io.github.open55.otx.domain.chain.port.SignedTx;
import io.github.open55.otx.domain.chain.port.SignerPort;
import io.github.open55.otx.domain.chain.port.TxBroadcastPort;
import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
import io.github.open55.otx.domain.ledger.port.ChainQueryPort;
import io.github.open55.otx.domain.ledger.port.ChainTxReceipt;
import io.github.open55.otx.domain.withdraw.WithdrawRequestStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 提现广播编排集成测试。
 * <p>
 * 被测对象：WithdrawAppService 的 broadcast / confirmAndSettle / cancelWithdraw /
 * queryStatus 四个链上用例，及其与提现请求记录、账户余额、资金流水、总账凭证的
 * 全链路协同。覆盖范围：广播→确认结算全流程（DRAFT 凭证留痕、冻结扣减、POSTED）、
 * 并发确认结算仅一次生效、确认数不足拒绝与达标后重试、广播后取消解冻、链上失败
 * 解冻并保留 DRAFT 凭证五个场景。签名（SignerPort）、广播（TxBroadcastPort）与
 * 链上查询（ChainQueryPort）三个出站端口全部 mock，无真实链上交互；依赖本地
 * MySQL:3307 / Redis:6380 与 Flyway 迁移完成的测试环境（dev profile）。
 * <p>
 * Withdraw broadcast orchestration integration tests.
 * Exercises WithdrawAppService broadcast/confirmAndSettle/cancelWithdraw/queryStatus
 * use cases together with withdraw request records, account balances, fund flows
 * and ledger journals end to end, with the signer, broadcast and chain-query
 * outbound ports fully mocked (no real chain interaction).
 */
@SpringBootTest(properties = "otx.chain-tx.hot-wallet-address=0x9c8f4b2d1e6a7f3b5c9d0e1f2a3b4c5d6e7f8a9b")
@DisplayName("提现广播编排集成测试 | Withdraw broadcast orchestration integration tests")
class WithdrawBroadcastIntegrationTest {

    /** 测试账户初始可用余额（对应场景中的 100） */
    private static final BigDecimal INITIAL_BALANCE_100 = new BigDecimal("100");

    /** 冻结/广播/结算/取消共用的提现金额 50 */
    private static final BigDecimal AMOUNT_50 = new BigDecimal("50");

    /** 冻结完成后可用余额的预期值 50 */
    private static final BigDecimal AVAILABLE_AFTER_FREEZE_50 = new BigDecimal("50");

    /** 冻结完成后冻结余额的预期值 50 */
    private static final BigDecimal FROZEN_AFTER_FREEZE_50 = new BigDecimal("50");

    /** 解冻（取消/链上失败）后可用余额复原的预期值 100 */
    private static final BigDecimal AVAILABLE_RESTORED_100 = new BigDecimal("100");

    /** 结算/解冻完成后冻结余额的预期值 0 */
    private static final BigDecimal FROZEN_ZERO = BigDecimal.ZERO;

    /** 测试统一使用的币种 */
    private static final String CURRENCY_USDT = "USDT";

    /** 测试链 ID（Sepolia 测试网，与 web3j.chain-id 配置一致） */
    private static final String CHAIN_ID_SEPOLIA = "11155111";

    /** 平台热钱包地址（测试用假地址，经配置注入 WithdrawAppServiceImpl.fromAddress） */
    private static final String HOT_WALLET_ADDRESS = "0x9c8f4b2d1e6a7f3b5c9d0e1f2a3b4c5d6e7f8a9b";

    /** 用户提现目标链上地址（测试用假地址） */
    private static final String TO_ADDRESS = "0x1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b";

    /** 代币合约地址（测试用假地址） */
    private static final String TOKEN_ADDRESS = "0xf0e1d2c3b4a5968778695a4b3c2d1e0f1a2b3c4d";

    /** mock 签名产出的原始交易串（任意非空十六进制串） */
    private static final String RAW_TRANSACTION = "0xf86b808504a817c800825208940000000000000000000000000000000000000000";

    /** mock 广播返回的链上交易哈希 */
    private static final String TX_HASH_MOCK = "0xabc123def4567890abcdef1234567890abcdef1234567890abcdef1234567890";

    /** 成功回执状态（0x1） */
    private static final String RECEIPT_STATUS_SUCCESS = "0x1";

    /** 回执区块高度 */
    private static final BigInteger RECEIPT_BLOCK_NUMBER = BigInteger.valueOf(100);

    /** 回执确认数（仅用于构造回执对象，确认阈值由 isConfirmed mock 控制） */
    private static final int RECEIPT_CONFIRMATIONS = 12;

    /** 回执交易金额（Wei） */
    private static final BigInteger RECEIPT_VALUE_WEI = new BigInteger("50000000000000000000");

    /** 冻结用例业务号前缀 */
    private static final String BIZ_NO_PREFIX_FREEZE = "WD-FREEZE-";

    /** 广播用例业务号前缀 */
    private static final String BIZ_NO_PREFIX_BROADCAST = "WD-BROADCAST-";

    /** 并发任务等待超时（秒） */
    private static final long CONCURRENT_TIMEOUT_SECONDS = 30L;

    /** 签名端口 mock：广播流程中 SignerPort.sign 返回 SignedTx */
    @MockitoBean
    private SignerPort signerPort;

    /** 广播端口 mock：广播流程中 currentNonce / broadcast 均走该 mock */
    @MockitoBean
    private TxBroadcastPort txBroadcastPort;

    /** 链上查询端口 mock：confirmAndSettle 的 queryTxReceipt / isConfirmed 均走该 mock */
    @MockitoBean
    private ChainQueryPort chainQueryPort;

    @Autowired
    private WithdrawAppService withdrawAppService;

    @Autowired
    private AccountAppService accountAppService;

    @Autowired
    private LedgerAppService ledgerAppService;

    @Autowired
    private FundFlowAppService fundFlowAppService;

    /** 当前测试的独立用户标识，由 nanoTime 生成避免测试间相互影响 */
    private Long uid;

    /** 冻结用例业务号 */
    private String freezeBizNo;

    /** 广播用例业务号 */
    private String broadcastBizNo;

    /**
     * 每个测试前创建独立账户并充值 100 可用余额，业务号带 uid 后缀保证全局唯一，
     * 避免测试之间相互影响。
     */
    @BeforeEach
    void setUp() {
        uid = System.nanoTime();
        freezeBizNo = BIZ_NO_PREFIX_FREEZE + uid;
        broadcastBizNo = BIZ_NO_PREFIX_BROADCAST + uid;

        accountAppService.createAccount(uid);
        accountAppService.increaseBalance(uid, INITIAL_BALANCE_100);
    }

    /**
     * 场景：广播→确认结算全流程以 POSTED 收尾。
     * <p>
     * 验证冻结后广播成功：请求 BROADCASTED、DRAFT 凭证携带链上字段
     * （DEBIT USER_FROZEN / CREDIT WITHDRAW_IN_TRANSIT）、余额无扣减；
     * confirmAndSettle 确认达标后：冻结扣减、WITHDRAW 流水一条、凭证 POSTED、
     * 请求 SETTLED。
     * <p>
     * Scenario: broadcast-then-confirm-settle full flow ends posted.
     */
    @Test
    @DisplayName("广播→确认结算全流程以 POSTED 收尾 | Broadcast-then-confirm-settle full flow ends posted")
    void broadcast_confirmSettle_fullFlow_endsPosted() {
        // Arrange：冻结 50 并 stub 签名/广播/链上确认端口
        withdrawAppService.freeze(newWithdrawRequest(uid, freezeBizNo, AMOUNT_50));
        stubChainPortsForBroadcastSuccess();
        stubChainQueryConfirmed();

        // Act：广播提现请求
        WithdrawBroadcastResponseDTO broadcastResponse = withdrawAppService.broadcast(newBroadcastRequest());

        // Assert：请求 BROADCASTED 且回填 mock 交易哈希
        assertEquals(WithdrawRequestStatusEnum.BROADCASTED.name(), broadcastResponse.getStatus(),
                "广播成功后请求状态应为 BROADCASTED");
        assertEquals(TX_HASH_MOCK, broadcastResponse.getTxHash(), "广播响应应携带链上交易哈希");
        assertStatus(broadcastBizNo, WithdrawRequestStatusEnum.BROADCASTED);

        // Assert：DRAFT 凭证存在且携带链上字段，分录为 DEBIT USER_FROZEN / CREDIT WITHDRAW_IN_TRANSIT
        JournalDetailResponseDTO draftJournal = ledgerAppService.findByBizNo(broadcastBizNo);
        assertEquals(LedgerJournalStatusEnum.DRAFT.name(), draftJournal.getStatus(), "广播后凭证应为 DRAFT");
        assertEquals(LedgerBizTypeEnum.WITHDRAW_ONCHAIN.name(), draftJournal.getBizType(),
                "凭证业务类型应为 WITHDRAW_ONCHAIN");
        assertEquals(TX_HASH_MOCK, draftJournal.getChainTxHash(), "DRAFT 凭证应携带链上交易哈希");
        assertEquals(CHAIN_ID_SEPOLIA, draftJournal.getChainId(), "DRAFT 凭证应携带链 ID");
        assertEquals(2, draftJournal.getEntries().size(), "DRAFT 凭证应包含两条分录");
        assertEntry(draftJournal, LedgerEntryTypeEnum.DEBIT, LedgerAccountCodeEnum.USER_FROZEN,
                AMOUNT_50, uid);
        assertEntry(draftJournal, LedgerEntryTypeEnum.CREDIT, LedgerAccountCodeEnum.WITHDRAW_IN_TRANSIT,
                AMOUNT_50, null);

        // Assert：广播不扣减余额（资金仍冻结），无 WITHDRAW 流水
        assertBalances(uid, AVAILABLE_AFTER_FREEZE_50, FROZEN_AFTER_FREEZE_50);
        assertEquals(0L, countFlowsByType(uid, broadcastBizNo, FundFlowTypeEnum.WITHDRAW),
                "广播成功不应产生 WITHDRAW 流水");

        // Act：确认结算
        WithdrawSettleResponseDTO settleResponse = withdrawAppService.confirmAndSettle(broadcastBizNo);

        // Assert：请求 SETTLED、凭证 POSTED
        assertEquals(WithdrawRequestStatusEnum.SETTLED.name(), settleResponse.getStatus(),
                "确认后请求状态应为 SETTLED");
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), settleResponse.getJournalStatus(),
                "确认后凭证状态应为 POSTED");
        assertStatus(broadcastBizNo, WithdrawRequestStatusEnum.SETTLED);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(),
                ledgerAppService.findByBizNo(broadcastBizNo).getStatus(), "结算后凭证状态应为 POSTED");

        // Assert：冻结余额扣减、WITHDRAW 流水恰好一条
        assertBalances(uid, AVAILABLE_AFTER_FREEZE_50, FROZEN_ZERO);
        assertSingleFlow(uid, broadcastBizNo, FundFlowTypeEnum.WITHDRAW, FundFlowDirectionEnum.OUT);
    }

    /**
     * 场景：并发确认结算仅一次完整生效。
     * <p>
     * 两个线程同时调用 confirmAndSettle，乐观锁与唯一索引兜底保证仅一次
     * 扣款、一条 WITHDRAW 流水、一张 POSTED 凭证；另一方幂等返回成功或被
     * 状态机拒绝，最终状态唯一。
     * <p>
     * Scenario: concurrent confirm-and-settle calls yield a single settlement.
     */
    @Test
    @DisplayName("并发确认结算仅一次完整生效 | Concurrent confirm-and-settle calls yield single settlement")
    void confirmSettle_concurrentCalls_onlyOneSettlement() throws Exception {
        // Arrange：走到 BROADCASTED 并 stub 链上确认达标
        freezeAndBroadcastToBroadcasted();
        stubChainQueryConfirmed();

        // Act：两个线程同时触发确认结算
        CountDownLatch startLatch = new CountDownLatch(1);
        CompletableFuture<String> firstFuture = settleAfterLatch(startLatch, broadcastBizNo);
        CompletableFuture<String> secondFuture = settleAfterLatch(startLatch, broadcastBizNo);
        startLatch.countDown();
        String firstResult = firstFuture.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String secondResult = secondFuture.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert：至少一个线程结算成功，且扣款一次、流水一条、凭证一张 POSTED
        assertTrue(firstResult.startsWith("SETTLED") || secondResult.startsWith("SETTLED"),
                "并发结算应至少一个线程成功，实际结果：" + firstResult + " / " + secondResult);
        assertStatus(broadcastBizNo, WithdrawRequestStatusEnum.SETTLED);
        assertBalances(uid, AVAILABLE_AFTER_FREEZE_50, FROZEN_ZERO);
        assertSingleFlow(uid, broadcastBizNo, FundFlowTypeEnum.WITHDRAW, FundFlowDirectionEnum.OUT);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(),
                ledgerAppService.findByBizNo(broadcastBizNo).getStatus(), "并发结算后凭证应 POSTED");
    }

    /**
     * 场景：确认数不足拒绝且达标后重试成功。
     * <p>
     * 先 mock isConfirmed=false 断言抛 TX_NOT_CONFIRMED_YET 且无任何状态变化
     * （请求仍 BROADCASTED、余额不变、凭证仍 DRAFT、无流水）；再 mock
     * isConfirmed=true 重试，断言完成结算。
     * <p>
     * Scenario: not-confirmed settle is rejected and retry succeeds after confirmed.
     */
    @Test
    @DisplayName("确认数不足拒绝且达标后重试成功 | Not-confirmed settle rejected and retry succeeds")
    void confirmSettle_notConfirmed_retryAfterConfirmed_succeeds() {
        // Arrange：走到 BROADCASTED；回执成功但确认数未达标
        freezeAndBroadcastToBroadcasted();
        when(chainQueryPort.queryTxReceipt(anyString(), anyString()))
                .thenReturn(Optional.of(newSuccessReceipt()));
        when(chainQueryPort.isConfirmed(anyString(), anyString(), anyInt())).thenReturn(false);

        // Act：第一次确认结算
        BizException firstEx = assertThrows(BizException.class,
                () -> withdrawAppService.confirmAndSettle(broadcastBizNo),
                "确认数不足时结算应抛 BizException");

        // Assert：错误码 TX_NOT_CONFIRMED_YET 且无任何状态变化
        assertEquals(BizErrorEnum.TX_NOT_CONFIRMED_YET.getCode(), firstEx.getErrorCode(),
                "确认数不足应抛出 TX_NOT_CONFIRMED_YET 错误码");
        assertStatus(broadcastBizNo, WithdrawRequestStatusEnum.BROADCASTED);
        assertBalances(uid, AVAILABLE_AFTER_FREEZE_50, FROZEN_AFTER_FREEZE_50);
        assertEquals(LedgerJournalStatusEnum.DRAFT.name(),
                ledgerAppService.findByBizNo(broadcastBizNo).getStatus(), "未确认时凭证应保持 DRAFT");
        assertEquals(0L, countFlowsByType(uid, broadcastBizNo, FundFlowTypeEnum.WITHDRAW),
                "未确认时不应产生 WITHDRAW 流水");

        // Arrange：确认数达标后重试（重新 stub 为已确认）
        when(chainQueryPort.isConfirmed(anyString(), anyString(), anyInt())).thenReturn(true);

        // Act：第二次确认结算
        WithdrawSettleResponseDTO settleResponse = withdrawAppService.confirmAndSettle(broadcastBizNo);

        // Assert：重试成功完成结算
        assertEquals(WithdrawRequestStatusEnum.SETTLED.name(), settleResponse.getStatus(),
                "重试后请求状态应为 SETTLED");
        assertBalances(uid, AVAILABLE_AFTER_FREEZE_50, FROZEN_ZERO);
        assertSingleFlow(uid, broadcastBizNo, FundFlowTypeEnum.WITHDRAW, FundFlowDirectionEnum.OUT);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(),
                ledgerAppService.findByBizNo(broadcastBizNo).getStatus(), "重试后凭证应 POSTED");
    }

    /**
     * 场景：广播后取消释放冻结资金。
     * <p>
     * BROADCASTED 状态调用 cancelWithdraw：请求置 CANCELLED、UNFREEZE 流水一条
     * （方向 IN）、冻结余额释放回可用余额（可用复原 100 / 冻结 0）。
     * <p>
     * Scenario: cancel after broadcast unfreezes funds.
     */
    @Test
    @DisplayName("广播后取消释放冻结资金 | Cancel after broadcast unfreezes funds")
    void cancelAfterBroadcast_unfreezes() {
        // Arrange：走到 BROADCASTED
        freezeAndBroadcastToBroadcasted();

        // Act：取消提现
        WithdrawStatusResponseDTO cancelResponse = withdrawAppService.cancelWithdraw(broadcastBizNo);

        // Assert：请求 CANCELLED、余额复原、UNFREEZE 流水一条
        assertEquals(WithdrawRequestStatusEnum.CANCELLED.name(), cancelResponse.getStatus(),
                "取消后请求状态应为 CANCELLED");
        assertStatus(broadcastBizNo, WithdrawRequestStatusEnum.CANCELLED);
        assertBalances(uid, AVAILABLE_RESTORED_100, FROZEN_ZERO);
        assertSingleFlow(uid, broadcastBizNo, FundFlowTypeEnum.UNFREEZE, FundFlowDirectionEnum.IN);
    }

    /**
     * 场景：链上失败解冻且保留 DRAFT 凭证。
     * <p>
     * mock queryTxReceipt 返回空（链上交易缺失）触发失败路径：请求置 FAILED、
     * 解冻资金（UNFREEZE 流水一条）、抛 TX_CHAIN_FAILED；DRAFT 凭证保留
     * 作为审计痕迹（状态仍为 DRAFT，不删除不冲销）。
     * <p>
     * Scenario: chain failure unfreezes funds and keeps the draft journal.
     */
    @Test
    @DisplayName("链上失败解冻且保留 DRAFT 凭证 | Chain failure unfreezes funds and keeps draft journal")
    void chainFailed_unfreezesAndKeepsDraftJournal() {
        // Arrange：走到 BROADCASTED；链上查不到该交易回执
        freezeAndBroadcastToBroadcasted();
        when(chainQueryPort.queryTxReceipt(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Act：确认结算
        BizException ex = assertThrows(BizException.class,
                () -> withdrawAppService.confirmAndSettle(broadcastBizNo),
                "链上交易缺失时结算应抛 BizException");

        // Assert：错误码 TX_CHAIN_FAILED、请求 FAILED、解冻、DRAFT 凭证保留
        assertEquals(BizErrorEnum.TX_CHAIN_FAILED.getCode(), ex.getErrorCode(),
                "链上失败应抛出 TX_CHAIN_FAILED 错误码");
        assertStatus(broadcastBizNo, WithdrawRequestStatusEnum.FAILED);
        assertBalances(uid, AVAILABLE_RESTORED_100, FROZEN_ZERO);
        assertSingleFlow(uid, broadcastBizNo, FundFlowTypeEnum.UNFREEZE, FundFlowDirectionEnum.IN);
        assertEquals(LedgerJournalStatusEnum.DRAFT.name(),
                ledgerAppService.findByBizNo(broadcastBizNo).getStatus(),
                "链上失败后 DRAFT 凭证应保留作为审计痕迹");
    }

    /**
     * 冻结并广播至 BROADCASTED 状态，供除全流程外的场景复用。
     * <p>
     * 冻结 50（可用 50/冻结 50）后 stub 签名与广播端口并执行广播，
     * 不 stub 链上查询端口（各场景按需覆盖）。
     */
    private void freezeAndBroadcastToBroadcasted() {
        withdrawAppService.freeze(newWithdrawRequest(uid, freezeBizNo, AMOUNT_50));
        stubChainPortsForBroadcastSuccess();
        withdrawAppService.broadcast(newBroadcastRequest());
    }

    /**
     * stub 签名与广播端口，使 broadcast 流程成功返回 mock 交易哈希。
     * <p>
     * 覆盖 nonce 查询、签名、广播三个外部 IO 步骤。
     */
    private void stubChainPortsForBroadcastSuccess() {
        when(txBroadcastPort.currentNonce(anyString(), anyString())).thenReturn(BigInteger.ZERO);
        when(signerPort.sign(any(SignRequest.class)))
                .thenReturn(new SignedTx(CHAIN_ID_SEPOLIA, RAW_TRANSACTION, HOT_WALLET_ADDRESS, TO_ADDRESS));
        when(txBroadcastPort.broadcast(any(SignedTx.class)))
                .thenReturn(new BroadcastResult(CHAIN_ID_SEPOLIA, TX_HASH_MOCK, HOT_WALLET_ADDRESS, TO_ADDRESS));
    }

    /**
     * stub 链上查询端口：回执成功且确认数达标。
     */
    private void stubChainQueryConfirmed() {
        when(chainQueryPort.queryTxReceipt(anyString(), anyString()))
                .thenReturn(Optional.of(newSuccessReceipt()));
        when(chainQueryPort.isConfirmed(anyString(), anyString(), anyInt())).thenReturn(true);
    }

    /**
     * 构造成功回执值对象：status 为 0x1，其余字段取测试常量。
     *
     * @return 链上成功交易回执
     */
    private ChainTxReceipt newSuccessReceipt() {
        return new ChainTxReceipt(CHAIN_ID_SEPOLIA, TX_HASH_MOCK, RECEIPT_BLOCK_NUMBER,
                RECEIPT_STATUS_SUCCESS, RECEIPT_CONFIRMATIONS, HOT_WALLET_ADDRESS, TO_ADDRESS,
                RECEIPT_VALUE_WEI);
    }

    /**
     * 构造冻结请求：仅需 uid、bizNo、amount、currency 四要素。
     *
     * @param uid    用户标识
     * @param bizNo  业务号（幂等键）
     * @param amount 金额
     * @return 提现请求
     */
    private WithdrawRequestDTO newWithdrawRequest(Long uid, String bizNo, BigDecimal amount) {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setUid(uid);
        request.setBizNo(bizNo);
        request.setAmount(amount);
        request.setCurrency(CURRENCY_USDT);
        return request;
    }

    /**
     * 构造广播请求：冻结四要素 + 链上要素（chainId/toAddress/tokenAddress）。
     *
     * @return 广播请求
     */
    private WithdrawRequestDTO newBroadcastRequest() {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setUid(uid);
        request.setBizNo(broadcastBizNo);
        request.setAmount(AMOUNT_50);
        request.setCurrency(CURRENCY_USDT);
        request.setChainId(CHAIN_ID_SEPOLIA);
        request.setToAddress(TO_ADDRESS);
        request.setTokenAddress(TOKEN_ADDRESS);
        return request;
    }

    /**
     * 断言提现请求状态与预期一致。
     *
     * @param bizNo    业务号
     * @param expected 预期状态
     */
    private void assertStatus(String bizNo, WithdrawRequestStatusEnum expected) {
        assertEquals(expected.name(), withdrawAppService.queryStatus(bizNo).getStatus(),
                "提现请求状态应等于 " + expected.name());
    }

    /**
     * 断言账户可用余额与冻结余额与预期一致（BigDecimal 按数值比较，忽略精度差异）。
     *
     * @param uid               用户标识
     * @param expectedAvailable 预期可用余额
     * @param expectedFrozen    预期冻结余额
     */
    private void assertBalances(Long uid, BigDecimal expectedAvailable, BigDecimal expectedFrozen) {
        GetAccountResponse account = accountAppService.getByUid(uid);
        assertEquals(0, expectedAvailable.compareTo(account.getAvailableBalance()), "可用余额应等于预期值");
        assertEquals(0, expectedFrozen.compareTo(account.getFrozenBalance()), "冻结余额应等于预期值");
    }

    /**
     * 统计指定用户的某业务号下指定类型的资金流水条数。
     *
     * @param uid   用户标识
     * @param bizNo 业务号
     * @param type  流水类型
     * @return 流水条数
     */
    private long countFlowsByType(Long uid, String bizNo, FundFlowTypeEnum type) {
        return fundFlowAppService.findByUid(uid).stream()
                .filter(flow -> bizNo.equals(flow.getBizNo()))
                .filter(flow -> type == flow.getType())
                .count();
    }

    /**
     * 断言指定用户的某业务号下恰好存在一条指定类型的流水且方向符合预期。
     *
     * @param uid       用户标识
     * @param bizNo     业务号
     * @param type      流水类型
     * @param direction 预期资金方向
     */
    private void assertSingleFlow(Long uid, String bizNo, FundFlowTypeEnum type, FundFlowDirectionEnum direction) {
        List<FundFlowEntity> flows = fundFlowAppService.findByUid(uid).stream()
                .filter(flow -> bizNo.equals(flow.getBizNo()))
                .filter(flow -> type == flow.getType())
                .toList();
        assertEquals(1, flows.size(), "bizNo=" + bizNo + " 应恰好存在一条 " + type.name() + " 流水");
        assertEquals(direction, flows.get(0).getDirection(), "流水方向应为 " + direction.name());
    }

    /**
     * 断言凭证中存在指定方向与科目的分录，且金额与 uid 符合预期。
     *
     * @param journal     凭证详情
     * @param entryType   分录方向（DEBIT/CREDIT）
     * @param accountCode 会计科目
     * @param amount      预期金额
     * @param uid         预期用户标识（平台科目可为 null）
     */
    private void assertEntry(JournalDetailResponseDTO journal, LedgerEntryTypeEnum entryType,
                             LedgerAccountCodeEnum accountCode, BigDecimal amount, Long uid) {
        LedgerEntryResponseDTO entry = journal.getEntries().stream()
                .filter(e -> entryType.name().equals(e.getEntryType()))
                .filter(e -> accountCode.name().equals(e.getAccountCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "凭证中缺少分录：" + entryType.name() + " " + accountCode.name()));
        assertEquals(0, amount.compareTo(entry.getAmount()), "分录金额应等于预期值");
        assertEquals(uid, entry.getUid(), "分录 uid 应等于预期值");
        assertNull(entry.getCounterparty(), "分录 counterparty 应为空");
    }

    /**
     * 等待起始门闩后执行确认结算，供并发场景的两个线程同时触发。
     * <p>
     * 结算成功返回 "SETTLED"；未获胜的一方可能因唯一索引幂等兜底抛
     * BizIdempotentException、或因乐观锁重试后状态机拒绝抛 BizException，
     * 统一返回 "REJECTED:&lt;类名/错误码&gt;"，由测试断言至少一个线程成功。
     *
     * @param startLatch 起始门闩，放行后并发进入确认结算用例
     * @param bizNo      业务号
     * @return 结算结果描述
     */
    private CompletableFuture<String> settleAfterLatch(CountDownLatch startLatch, String bizNo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("并发测试等待门闩被中断", e);
            }
            try {
                withdrawAppService.confirmAndSettle(bizNo);
                return "SETTLED";
            } catch (BizException e) {
                return "REJECTED:" + e.getErrorCode();
            } catch (BizIdempotentException e) {
                return "REJECTED:BIZ_IDEMPOTENT";
            }
        });
    }
}
