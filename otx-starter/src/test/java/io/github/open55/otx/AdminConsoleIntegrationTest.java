package io.github.open55.otx;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.application.withdraw.dto.request.WithdrawRequestDTO;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.domain.chain.port.BroadcastResult;
import io.github.open55.otx.domain.chain.port.SignRequest;
import io.github.open55.otx.domain.chain.port.SignedTx;
import io.github.open55.otx.domain.chain.port.SignerPort;
import io.github.open55.otx.domain.chain.port.TxBroadcastPort;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理控制台页面渲染集成测试。
 * <p>
 * 造数链路（签名/广播/链上查询出站端口全部 mock）：开户 + 加余额 → 提现冻结
 * （落 FREEZE 流水与冻结凭证）→ 提现广播（保存提现请求记录、置 BROADCASTED、
 * 创建 DRAFT 凭证）→ 直接过账一张充值凭证；随后用 MockMvc 请求 /admin 四个
 * 页面，断言返回 200 且响应体包含关键数据。依赖本地 MySQL:3307 / Redis:6380
 * 与 Flyway 迁移完成的测试环境（dev profile），数据用 nanoTime 隔离。
 * <p>
 * Admin console page rendering integration tests. Seeds data through the
 * account/deposit/freeze/broadcast/post-journal use cases (signer, broadcast
 * and chain-query outbound ports mocked) and asserts the admin pages return
 * 200 with the key data rendered.
 */
@SpringBootTest(properties = "otx.chain-tx.hot-wallet-address=0x9c8f4b2d1e6a7f3b5c9d0e1f2a3b4c5d6e7f8a9b")
@AutoConfigureMockMvc
@DisplayName("管理控制台页面渲染集成测试 | Admin console page rendering integration tests")
class AdminConsoleIntegrationTest {

    /** 造数账户的初始可用余额 */
    private static final BigDecimal INITIAL_BALANCE_100 = new BigDecimal("100");

    /** 提现冻结/广播金额 */
    private static final BigDecimal AMOUNT_50 = new BigDecimal("50");

    /** 过账凭证的单条分录金额 */
    private static final BigDecimal ENTRY_AMOUNT_100 = new BigDecimal("100");

    /** 测试统一使用的币种 */
    private static final String CURRENCY_USDT = "USDT";

    /** 测试链 ID（Sepolia 测试网，与 web3j.chain-id 配置一致） */
    private static final String CHAIN_ID = "11155111";

    /** 平台热钱包地址（经配置注入 WithdrawAppServiceImpl.fromAddress） */
    private static final String HOT_WALLET_ADDRESS = "0x9c8f4b2d1e6a7f3b5c9d0e1f2a3b4c5d6e7f8a9b";

    /** 用户提现目标链上地址（测试用假地址） */
    private static final String TO_ADDRESS = "0x1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b";

    /** 代币合约地址（测试用假地址） */
    private static final String TOKEN_ADDRESS = "0xf0e1d2c3b4a5968778695a4b3c2d1e0f1a2b3c4d";

    /** mock 签名产出的原始交易串（任意非空十六进制串） */
    private static final String RAW_TRANSACTION = "0xf86b808504a817c800825208940000000000000000000000000000000000000000";

    /** mock 广播返回的链上交易哈希 */
    private static final String TX_HASH_MOCK = "0xabc123def4567890abcdef1234567890abcdef1234567890abcdef1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountAppService accountAppService;

    @Autowired
    private WithdrawAppService withdrawAppService;

    @Autowired
    private LedgerAppService ledgerAppService;

    /** 签名端口 mock，避免真实 keystore 交互 */
    @MockitoBean
    private SignerPort signerPort;

    /** 广播端口 mock，避免真实链上广播 */
    @MockitoBean
    private TxBroadcastPort txBroadcastPort;

    /**
     * 场景：控制台四个页面渲染真实数据。
     * Scenario: the four admin console pages render the seeded data.
     * 造数（开户/加余额/提现冻结/提现广播/凭证过账）后请求 /admin、
     * /admin/accounts、/admin/withdraws、/admin/journals 与流水页，
     * 断言 200 且响应体包含关键数据。
     */
    @Test
    @DisplayName("控制台四个页面渲染真实数据 | admin pages render seeded data")
    void adminPages_renderWithData() throws Exception {
        // Arrange：以 nanoTime 生成隔离的 uid 与业务号，避免与既有数据冲突
        long uid = System.nanoTime() % 100000000L;
        String freezebizNo = "ADMIN-FREEZE-" + uid;
        String broadcastBizNo = "ADMIN-BROADCAST-" + uid;
        String journalBizNo = "ADMIN-JOURNAL-" + uid;
        when(txBroadcastPort.currentNonce(CHAIN_ID, HOT_WALLET_ADDRESS)).thenReturn(BigInteger.ONE);
        when(signerPort.sign(any(SignRequest.class)))
                .thenReturn(new SignedTx(CHAIN_ID, RAW_TRANSACTION, HOT_WALLET_ADDRESS, TO_ADDRESS));
        when(txBroadcastPort.broadcast(any(SignedTx.class)))
                .thenReturn(new BroadcastResult(CHAIN_ID, TX_HASH_MOCK, HOT_WALLET_ADDRESS, TO_ADDRESS));

        accountAppService.createAccount(uid);
        accountAppService.increaseBalance(uid, INITIAL_BALANCE_100);
        withdrawAppService.freeze(newWithdrawRequest(uid, freezebizNo));
        withdrawAppService.broadcast(newWithdrawRequest(uid, broadcastBizNo));
        ledgerAppService.postJournal(buildJournalRequest(journalBizNo));

        // Act & Assert：四个页面均返回 200 且包含关键数据
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OTX 管理控制台")));
        mockMvc.perform(get("/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(String.valueOf(uid))));
        mockMvc.perform(get("/admin/withdraws"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(broadcastBizNo)));
        mockMvc.perform(get("/admin/journals"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(journalBizNo)));
        // 凭证详情页渲染分录表与借贷合计（SpEL 投影）
        mockMvc.perform(get("/admin/journals/{bizNo}", journalBizNo))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(journalBizNo)))
                .andExpect(content().string(containsString("借贷平衡")));
        // 流水时间线页复用 findByUid 数据源，验证该用户流水可渲染
        mockMvc.perform(get("/admin/accounts/{uid}/flows", uid))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(freezebizNo)));
    }

    /**
     * 构造提现请求：冻结用例仅需 uid/bizNo/amount/currency，
     * 广播用例额外需要 chainId / toAddress / tokenAddress。
     *
     * @param uid   用户唯一标识
     * @param bizNo 业务流水号
     * @return 提现请求
     */
    private static WithdrawRequestDTO newWithdrawRequest(long uid, String bizNo) {
        WithdrawRequestDTO request = new WithdrawRequestDTO();
        request.setUid(uid);
        request.setBizNo(bizNo);
        request.setAmount(AMOUNT_50);
        request.setCurrency(CURRENCY_USDT);
        request.setChainId(CHAIN_ID);
        request.setToAddress(TO_ADDRESS);
        request.setTokenAddress(TOKEN_ADDRESS);
        return request;
    }

    /**
     * 构造一条借贷平衡的充值过账请求。
     *
     * @param bizNo 业务流水号
     * @return 过账请求
     */
    private static PostJournalRequestDTO buildJournalRequest(String bizNo) {
        PostJournalRequestDTO req = new PostJournalRequestDTO();
        req.setBizNo(bizNo);
        req.setBizType(LedgerBizTypeEnum.DEPOSIT_ONCHAIN);
        req.setCurrency(CURRENCY_USDT);
        req.setPostingDate(LocalDate.now());
        req.setDescription("集成测试-充值入账");

        LedgerEntryRequestDTO debit = new LedgerEntryRequestDTO();
        debit.setAccountCode(LedgerAccountCodeEnum.PLATFORM_HOT);
        debit.setEntryType(LedgerEntryTypeEnum.DEBIT);
        debit.setAmount(ENTRY_AMOUNT_100);

        LedgerEntryRequestDTO credit = new LedgerEntryRequestDTO();
        credit.setAccountCode(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT);
        credit.setEntryType(LedgerEntryTypeEnum.CREDIT);
        credit.setAmount(ENTRY_AMOUNT_100);

        req.setEntries(List.of(debit, credit));
        return req;
    }
}
