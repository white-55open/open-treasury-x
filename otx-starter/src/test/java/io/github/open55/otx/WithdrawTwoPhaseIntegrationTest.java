package io.github.open55.otx;

import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.dto.response.LedgerEntryResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.application.withdraw.dto.request.WithdrawRequestDTO;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提现两阶段（冻结-结算-解冻）集成测试。
 * <p>
 * 被测对象：WithdrawAppService 的 freeze / withdraw / unfreeze 三个用例，
 * 以及它们与账户余额、资金流水、总账凭证之间的全链路协同。
 * 覆盖范围：冻结→结算全流程、冻结→解冻全流程、同 bizNo 重复调用幂等、
 * 并发同 bizNo 唯一索引兜底、冻结余额不足拒绝五个场景。
 * 依赖数据库 Flyway 迁移已完成且存在测试环境数据。
 * <p>
 * Two-phase withdraw (freeze-settle-unfreeze) integration tests.
 * Exercises WithdrawAppService freeze/withdraw/unfreeze use cases together
 * with account balances, fund flows and ledger journals end to end.
 */
@SpringBootTest
@DisplayName("提现两阶段集成测试 | Withdraw two-phase integration tests")
class WithdrawTwoPhaseIntegrationTest {

    /** 测试账户初始可用余额（对应场景中的 100） */
    private static final BigDecimal INITIAL_BALANCE_100 = new BigDecimal("100");

    /** 冻结/结算/解冻的通用金额 50 */
    private static final BigDecimal AMOUNT_50 = new BigDecimal("50");

    /** 低余额账户的可用余额 30（用于余额不足场景） */
    private static final BigDecimal LOW_BALANCE_30 = new BigDecimal("30");

    /** 超过可用余额的冻结金额 60（用于余额不足场景） */
    private static final BigDecimal EXCESS_FREEZE_60 = new BigDecimal("60");

    /** 冻结完成后可用余额的预期值 50 */
    private static final BigDecimal AVAILABLE_AFTER_FREEZE_50 = new BigDecimal("50");

    /** 冻结完成后冻结余额的预期值 50 */
    private static final BigDecimal FROZEN_AFTER_FREEZE_50 = new BigDecimal("50");

    /** 解冻完成后可用余额复原的预期值 100 */
    private static final BigDecimal AVAILABLE_RESTORED_100 = new BigDecimal("100");

    /** 全部流程结束后冻结余额的预期值 0 */
    private static final BigDecimal FROZEN_ZERO = BigDecimal.ZERO;

    /** 测试统一使用的币种 */
    private static final String CURRENCY_USDT = "USDT";

    /** 冻结用例业务号前缀 */
    private static final String BIZ_NO_PREFIX_FREEZE = "WD-FREEZE-";

    /** 结算用例业务号前缀 */
    private static final String BIZ_NO_PREFIX_SETTLE = "WD-SETTLE-";

    /** 解冻用例业务号前缀 */
    private static final String BIZ_NO_PREFIX_UNFREEZE = "WD-UNFREEZE-";

    /** 失败场景业务号前缀 */
    private static final String BIZ_NO_PREFIX_FAIL = "WD-FAIL-";

    /** 并发提交的线程数 */
    private static final int CONCURRENT_THREADS = 2;

    /** 并发任务等待超时（秒） */
    private static final long CONCURRENT_TIMEOUT_SECONDS = 30L;

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

    /** 结算用例业务号 */
    private String settleBizNo;

    /** 解冻用例业务号 */
    private String unfreezeBizNo;

    /**
     * 每个测试前创建独立账户并充值 100 可用余额，业务号带 uid 后缀保证全局唯一，
     * 避免测试之间相互影响。
     */
    @BeforeEach
    void setUp() {
        uid = System.nanoTime();
        freezeBizNo = BIZ_NO_PREFIX_FREEZE + uid;
        settleBizNo = BIZ_NO_PREFIX_SETTLE + uid;
        unfreezeBizNo = BIZ_NO_PREFIX_UNFREEZE + uid;

        accountAppService.createAccount(uid);
        accountAppService.increaseBalance(uid, INITIAL_BALANCE_100);
    }

    /**
     * 场景：冻结→结算全流程。
     * <p>
     * 验证账户可用 100 时冻结 50（可用 50/冻结 50），再结算 50（可用 50/冻结 0）；
     * 冻结与结算各落一条流水与一张 POSTED 凭证，且分录方向符合复式记账约定。
     * <p>
     * Scenario: freeze-then-settle full flow.
     */
    @Test
    @DisplayName("冻结→结算全流程余额与凭证正确 | Freeze-then-settle full flow updates balances and journals")
    void freezeThenSettle_fullFlow_updatesBalancesAndJournals() {
        // Arrange：账户已由 setUp 充值 100 可用余额，构造冻结与结算请求
        WithdrawRequestDTO freezeRequest = newWithdrawRequest(uid, freezeBizNo, AMOUNT_50);
        WithdrawRequestDTO settleRequest = newWithdrawRequest(uid, settleBizNo, AMOUNT_50);

        // Act：先冻结 50，再结算 50
        withdrawAppService.freeze(freezeRequest);
        assertBalances(uid, AVAILABLE_AFTER_FREEZE_50, FROZEN_AFTER_FREEZE_50);
        withdrawAppService.withdraw(settleRequest);

        // Assert：结算后可用余额 50、冻结余额 0
        assertBalances(uid, AVAILABLE_AFTER_FREEZE_50, FROZEN_ZERO);

        // 冻结流水与结算流水各一条
        assertEquals(1L, countFlowsByBizNo(uid, freezeBizNo), "冻结应恰好产生一条 FREEZE 流水");
        assertEquals(1L, countFlowsByBizNo(uid, settleBizNo), "结算应恰好产生一条 WITHDRAW 流水");

        // 冻结凭证：POSTED，分录为 DEBIT USER_AVAILABLE / CREDIT USER_FROZEN
        JournalDetailResponseDTO freezeJournal = ledgerAppService.findByBizNo(freezeBizNo);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), freezeJournal.getStatus(), "冻结凭证应已过账");
        assertEquals(LedgerBizTypeEnum.FREEZE.name(), freezeJournal.getBizType(), "冻结凭证业务类型应为 FREEZE");
        assertEquals(2, freezeJournal.getEntries().size(), "冻结凭证应包含两条分录");
        assertEntry(freezeJournal, LedgerEntryTypeEnum.DEBIT, LedgerAccountCodeEnum.USER_AVAILABLE,
                AMOUNT_50, uid);
        assertEntry(freezeJournal, LedgerEntryTypeEnum.CREDIT, LedgerAccountCodeEnum.USER_FROZEN,
                AMOUNT_50, uid);

        // 结算凭证：POSTED，分录为 DEBIT USER_FROZEN / CREDIT WITHDRAW_IN_TRANSIT（uid 为空）
        JournalDetailResponseDTO settleJournal = ledgerAppService.findByBizNo(settleBizNo);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), settleJournal.getStatus(), "结算凭证应已过账");
        assertEquals(LedgerBizTypeEnum.WITHDRAW_ONCHAIN.name(), settleJournal.getBizType(),
                "结算凭证业务类型应为 WITHDRAW_ONCHAIN");
        assertEquals(2, settleJournal.getEntries().size(), "结算凭证应包含两条分录");
        assertEntry(settleJournal, LedgerEntryTypeEnum.DEBIT, LedgerAccountCodeEnum.USER_FROZEN,
                AMOUNT_50, uid);
        assertEntry(settleJournal, LedgerEntryTypeEnum.CREDIT, LedgerAccountCodeEnum.WITHDRAW_IN_TRANSIT,
                AMOUNT_50, null);
    }

    /**
     * 场景：冻结→解冻全流程。
     * <p>
     * 验证账户可用 100 时冻结 50（可用 50/冻结 50），再解冻 50 后余额完全复原
     * （可用 100/冻结 0）；解冻落一条 UNFREEZE 流水与一张 POSTED 凭证，
     * 原冻结凭证保持 POSTED 状态不被冲销。
     * <p>
     * Scenario: freeze-then-unfreeze full flow.
     */
    @Test
    @DisplayName("冻结→解冻全流程余额复原 | Freeze-then-unfreeze full flow restores balances")
    void freezeThenUnfreeze_fullFlow_restoresBalances() {
        // Arrange：账户已由 setUp 充值 100 可用余额，构造冻结与解冻请求
        WithdrawRequestDTO freezeRequest = newWithdrawRequest(uid, freezeBizNo, AMOUNT_50);
        WithdrawRequestDTO unfreezeRequest = newWithdrawRequest(uid, unfreezeBizNo, AMOUNT_50);

        // Act：先冻结 50，再解冻 50
        withdrawAppService.freeze(freezeRequest);
        withdrawAppService.unfreeze(unfreezeRequest);

        // Assert：余额复原为可用 100、冻结 0
        assertBalances(uid, AVAILABLE_RESTORED_100, FROZEN_ZERO);

        // 解冻流水恰好一条
        assertEquals(1L, countFlowsByBizNo(uid, unfreezeBizNo), "解冻应恰好产生一条 UNFREEZE 流水");

        // 解冻凭证：POSTED，分录为 DEBIT USER_FROZEN / CREDIT USER_AVAILABLE
        JournalDetailResponseDTO unfreezeJournal = ledgerAppService.findByBizNo(unfreezeBizNo);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), unfreezeJournal.getStatus(), "解冻凭证应已过账");
        assertEquals(LedgerBizTypeEnum.UNFREEZE.name(), unfreezeJournal.getBizType(),
                "解冻凭证业务类型应为 UNFREEZE");
        assertEquals(2, unfreezeJournal.getEntries().size(), "解冻凭证应包含两条分录");
        assertEntry(unfreezeJournal, LedgerEntryTypeEnum.DEBIT, LedgerAccountCodeEnum.USER_FROZEN,
                AMOUNT_50, uid);
        assertEntry(unfreezeJournal, LedgerEntryTypeEnum.CREDIT, LedgerAccountCodeEnum.USER_AVAILABLE,
                AMOUNT_50, uid);

        // 原冻结凭证保持 POSTED，不被冲销
        JournalDetailResponseDTO freezeJournal = ledgerAppService.findByBizNo(freezeBizNo);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), freezeJournal.getStatus(),
                "解冻后原冻结凭证应保持 POSTED 状态");
    }

    /**
     * 场景：同一 bizNo 重复调用幂等。
     * <p>
     * 验证冻结与结算各自对相同 bizNo 的第二次调用不产生任何副作用：
     * 余额只变化一次、流水与凭证各只有一条。
     * <p>
     * Scenario: duplicate bizNo second call has no double effect.
     */
    @Test
    @DisplayName("同一 bizNo 重复调用幂等无副作用 | Duplicate bizNo calls cause no double effect")
    void duplicateBizNo_secondCall_noDoubleEffect() {
        // Arrange：构造冻结与结算请求，均携带固定 bizNo
        WithdrawRequestDTO freezeRequest = newWithdrawRequest(uid, freezeBizNo, AMOUNT_50);
        WithdrawRequestDTO settleRequest = newWithdrawRequest(uid, settleBizNo, AMOUNT_50);

        // Act：冻结同一 bizNo 两次，再结算同一 bizNo 两次
        withdrawAppService.freeze(freezeRequest);
        withdrawAppService.freeze(freezeRequest);
        withdrawAppService.withdraw(settleRequest);
        withdrawAppService.withdraw(settleRequest);

        // Assert：余额只变化一次（可用 50/冻结 0）
        assertBalances(uid, AVAILABLE_AFTER_FREEZE_50, FROZEN_ZERO);

        // FREEZE 流水与凭证各一条
        assertEquals(1L, countFlowsByBizNo(uid, freezeBizNo), "重复冻结后 FREEZE 流水应只有一条");
        JournalDetailResponseDTO freezeJournal = ledgerAppService.findByBizNo(freezeBizNo);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), freezeJournal.getStatus(), "冻结凭证应已过账");
        assertEquals(2, freezeJournal.getEntries().size(), "冻结凭证应包含两条分录");

        // WITHDRAW 流水与凭证各一条
        assertEquals(1L, countFlowsByBizNo(uid, settleBizNo), "重复结算后 WITHDRAW 流水应只有一条");
        JournalDetailResponseDTO settleJournal = ledgerAppService.findByBizNo(settleBizNo);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), settleJournal.getStatus(), "结算凭证应已过账");
        assertEquals(2, settleJournal.getEntries().size(), "结算凭证应包含两条分录");
    }

    /**
     * 场景：并发同 bizNo 双线程提交。
     * <p>
     * 验证两个线程同时冻结同一 bizNo 时，数据库唯一索引兜底保证最终
     * FREEZE 流水与凭证各只有一条，余额只扣减一次。
     * <p>
     * Scenario: concurrent same bizNo submissions yield a single record.
     */
    @Test
    @DisplayName("并发同 bizNo 由唯一索引兜底仅落一条 | Concurrent same bizNo submissions yield single record")
    void concurrentSameBizNo_uniqueIndexGuaranteesSingleRecord() throws Exception {
        // Arrange：两个线程各持一份相同 bizNo 的冻结请求
        CountDownLatch startLatch = new CountDownLatch(1);
        CompletableFuture<String> firstFuture = CompletableFuture.supplyAsync(() ->
                freezeAfterLatch(startLatch, newWithdrawRequest(uid, freezeBizNo, AMOUNT_50)));
        CompletableFuture<String> secondFuture = CompletableFuture.supplyAsync(() ->
                freezeAfterLatch(startLatch, newWithdrawRequest(uid, freezeBizNo, AMOUNT_50)));

        // Act：同时放行两个线程并发冻结
        startLatch.countDown();
        firstFuture.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        secondFuture.get(CONCURRENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert：余额只扣一次（可用 50/冻结 50），流水与凭证各一条
        assertBalances(uid, AVAILABLE_AFTER_FREEZE_50, FROZEN_AFTER_FREEZE_50);
        assertEquals(1L, countFlowsByBizNo(uid, freezeBizNo), "并发冻结后 FREEZE 流水应只有一条");
        JournalDetailResponseDTO freezeJournal = ledgerAppService.findByBizNo(freezeBizNo);
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), freezeJournal.getStatus(), "冻结凭证应已过账");
        assertEquals(2, freezeJournal.getEntries().size(), "冻结凭证应包含两条分录");
    }

    /**
     * 场景：冻结余额不足被拒绝。
     * <p>
     * 验证账户可用 30 冻结 60 时抛 BizException 且错误码为 INSUFFICIENT_BALANCE，
     * 余额不变、无流水、无凭证，冻结失败无任何副作用。
     * <p>
     * Scenario: freeze with insufficient balance is rejected.
     */
    @Test
    @DisplayName("冻结余额不足拒绝且无副作用 | Freeze with insufficient balance is rejected")
    void freeze_insufficientBalance_throwsInsufficientBalance() {
        // Arrange：独立账户仅充值 30 可用余额，构造冻结 60 的请求
        Long failUid = System.nanoTime();
        String failBizNo = BIZ_NO_PREFIX_FAIL + failUid;
        accountAppService.createAccount(failUid);
        accountAppService.increaseBalance(failUid, LOW_BALANCE_30);
        WithdrawRequestDTO request = newWithdrawRequest(failUid, failBizNo, EXCESS_FREEZE_60);

        // Act：冻结金额超过可用余额
        BizException ex = assertThrows(BizException.class, () -> withdrawAppService.freeze(request),
                "可用余额不足时冻结应抛 BizException");

        // Assert：错误码为 INSUFFICIENT_BALANCE，余额不变，无流水无凭证
        assertEquals(BizErrorEnum.INSUFFICIENT_BALANCE.getCode(), ex.getErrorCode(),
                "冻结超额应抛出 INSUFFICIENT_BALANCE 错误码");
        assertBalances(failUid, LOW_BALANCE_30, FROZEN_ZERO);
        assertTrue(fundFlowAppService.findByUid(failUid).isEmpty(), "冻结失败不应产生任何资金流水");
        assertThrows(BizException.class, () -> ledgerAppService.findByBizNo(failBizNo),
                "冻结失败不应产生任何凭证");
    }

    /**
     * 构造提现请求，四个用例共用同一 DTO 结构。
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
     * 统计指定用户的某业务号下资金流水条数。
     *
     * @param uid   用户标识
     * @param bizNo 业务号
     * @return 流水条数
     */
    private long countFlowsByBizNo(Long uid, String bizNo) {
        return fundFlowAppService.findByUid(uid).stream()
                .filter(flow -> bizNo.equals(flow.getBizNo()))
                .count();
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
     * 等待起始门闩后执行冻结，供并发场景的两个线程同时触发。
     *
     * @param startLatch 起始门闩，放行后并发进入冻结用例
     * @param request    冻结请求
     * @return 业务号
     */
    private String freezeAfterLatch(CountDownLatch startLatch, WithdrawRequestDTO request) {
        try {
            startLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("并发测试等待门闩被中断", e);
        }
        return withdrawAppService.freeze(request);
    }
}
