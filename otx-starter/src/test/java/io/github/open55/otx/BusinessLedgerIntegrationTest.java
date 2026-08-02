package io.github.open55.otx;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.deposit.dto.DepositRequestDTO;
import io.github.open55.otx.application.deposit.service.DepositAppService;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
import io.github.open55.otx.domain.ledger.port.ChainQueryPort;
import io.github.open55.otx.domain.ledger.port.ChainTxReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 业务流串联总账集成测试。
 * <p>
 * 验证充值操作完成后总账模块中自动产生了对应的会计凭证。
 * 依赖数据库 Flyway 迁移已完成且存在测试环境数据；链上查询端口 mock，
 * 充值确认闸门（isConfirmed / queryTxReceipt）走 mock 保证确定性。
 * 提现场景的集成测试因领域约束（需先冻结余额）在单测中覆盖。
 * English: Business-ledger integration tests, verifying the deposit flow
 * auto-posts the accounting journal, with the chain-query port mocked.
 */
@SpringBootTest
@DisplayName("业务流串联总账集成测试 | Business-Ledger integration tests")
class BusinessLedgerIntegrationTest {

    /** 测试区块链 ID（Sepolia 测试网） */
    private static final String CHAIN_ID_SEPOLIA = "11155111";

    /** 测试链上交易哈希（mock 值） */
    private static final String TX_HASH = "0xabc123def4567890abcdef1234567890abcdef1234567890abcdef1234567890";

    /** 测试回执区块高度 */
    private static final BigInteger RECEIPT_BLOCK_NUMBER = BigInteger.valueOf(100L);

    /** 测试回执确认数（仅用于构造回执对象，确认阈值由 isConfirmed mock 控制） */
    private static final int RECEIPT_CONFIRMATIONS = 12;

    /** 测试回执交易金额（Wei） */
    private static final BigInteger RECEIPT_VALUE_WEI = BigInteger.ZERO;

    /** 充值金额 */
    private static final BigDecimal DEPOSIT_AMOUNT_100 = new BigDecimal("100");

    /** 测试币种 */
    private static final String DEPOSIT_CURRENCY = "USDT";

    /** 链上查询端口 mock：充值确认闸门的 isConfirmed / queryTxReceipt 均走该 mock */
    @MockitoBean
    private ChainQueryPort chainQueryPort;

    @Autowired
    private DepositAppService depositAppService;

    @Autowired
    private AccountAppService accountAppService;

    @Autowired
    private LedgerAppService ledgerAppService;

    private Long uid;
    private String depositBizNo;

    /**
     * 每个测试前创建独立账户和业务流水号，避免测试间相互影响。
     */
    @BeforeEach
    void setUp() {
        uid = System.nanoTime();
        depositBizNo = "INTG-DEP-" + uid;

        accountAppService.createAccount(uid);
        accountAppService.increaseBalance(uid, new BigDecimal("1000"));
    }

    /**
     * 场景：携带链上证据的充值请求在确认闸门通过后触发总账过账，凭证状态为 POSTED。
     * Scenario: a deposit carrying chain evidence passes the confirmation gate and the journal is posted.
     */
    @Test
    @DisplayName("充值触发总账过账 | deposit triggers journal posting")
    void deposit_triggersJournalPosting() {
        // Arrange：stub 链上确认通过与回执可查，构造带链上证据的充值请求
        stubChainConfirmed();
        DepositRequestDTO request = new DepositRequestDTO();
        request.setUid(uid);
        request.setBizNo(depositBizNo);
        request.setAmount(DEPOSIT_AMOUNT_100);
        request.setCurrency(DEPOSIT_CURRENCY);
        request.setChainId(CHAIN_ID_SEPOLIA);
        request.setChainTxHash(TX_HASH);

        // Act：执行充值入账
        depositAppService.deposit(request);

        // Assert：总账存在对应凭证且状态为 POSTED、分录两条
        JournalDetailResponseDTO journal = ledgerAppService.findByBizNo(depositBizNo);
        assertNotNull(journal, "充值后应能在总账中查到对应凭证");
        assertEquals(depositBizNo, journal.getBizNo());
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), journal.getStatus());
        assertEquals(2, journal.getEntries().size(), "凭证应包含两条分录");
    }

    /**
     * stub 链上确认闸门：确认达标且回执可查（区块高度填充凭证链字段）。
     */
    private void stubChainConfirmed() {
        when(chainQueryPort.isConfirmed(eq(CHAIN_ID_SEPOLIA), eq(TX_HASH), anyInt())).thenReturn(true);
        when(chainQueryPort.queryTxReceipt(eq(CHAIN_ID_SEPOLIA), eq(TX_HASH))).thenReturn(
                Optional.of(new ChainTxReceipt(CHAIN_ID_SEPOLIA, TX_HASH, RECEIPT_BLOCK_NUMBER,
                        "0x1", RECEIPT_CONFIRMATIONS, "0xFrom", "0xTo", RECEIPT_VALUE_WEI)));
    }
}
