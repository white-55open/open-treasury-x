package io.github.open55.otx;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.deposit.service.DepositAppService;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 业务流串联总账集成测试。
 * <p>
 * 验证充值操作完成后总账模块中自动产生了对应的会计凭证。
 * 依赖数据库 Flyway 迁移已完成且存在测试环境数据。
 * 提现场景的集成测试因领域约束（需先冻结余额）在单测中覆盖。
 */
@SpringBootTest
@DisplayName("业务流串联总账集成测试 | Business-Ledger integration tests")
class BusinessLedgerIntegrationTest {

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
     * 充值后总账存在对应凭证且状态为 POSTED。
     */
    @Test
    @DisplayName("充值触发总账过账")
    void deposit_triggersJournalPosting() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setUid(uid);
        request.setBizNo(depositBizNo);
        request.setAmount(new BigDecimal("100"));
        request.setCurrency("USDT");

        depositAppService.deposit(request);

        JournalDetailResponseDTO journal = ledgerAppService.findByBizNo(depositBizNo);
        assertNotNull(journal, "充值后应能在总账中查到对应凭证");
        assertEquals(depositBizNo, journal.getBizNo());
        assertEquals(LedgerJournalStatusEnum.POSTED.name(), journal.getStatus());
        assertEquals(2, journal.getEntries().size(), "凭证应包含两条分录");
    }
}
