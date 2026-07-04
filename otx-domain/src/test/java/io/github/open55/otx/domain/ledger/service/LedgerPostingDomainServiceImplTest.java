package io.github.open55.otx.domain.ledger.service;

import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.ledger.LedgerEntryEntity;
import io.github.open55.otx.domain.ledger.LedgerJournalEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LedgerPostingDomainServiceImpl 过账领域服务单元测试。
 * <p>
 * 覆盖服务正常编排（过账后状态为 POSTED）以及
 * 不变量违反时（借贷不等、同户同向重复）抛出对应异常。
 */
@DisplayName("LedgerPostingDomainServiceImpl 过账领域服务单元测试")
class LedgerPostingDomainServiceImplTest {

    private final LedgerPostingDomainServiceImpl service = new LedgerPostingDomainServiceImpl();

    private static final String BIZ_NO = "SVC-001";
    private static final LedgerBizTypeEnum BIZ_TYPE = LedgerBizTypeEnum.DEPOSIT_ONCHAIN;
    private static final String CURRENCY = "USDT";
    private static final LocalDate POSTING_DATE = LocalDate.of(2026, 1, 1);

    /**
     * 过账服务正常编排：一对借贷相等的分录过账后状态变为 POSTED。
     */
    @Test
    @DisplayName("正常过账服务编排后状态为 POSTED")
    void postJournal_withValidJournal_succeeds() {
        LedgerJournalEntity journal = LedgerJournalEntity.create(
                BIZ_NO, BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                List.of(
                        new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                new BigDecimal("100"), null, null, null, null),
                        new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                                new BigDecimal("100"), null, null, null, null)),
                null, null, null, null);
        service.postJournal(journal);
        assertEquals(LedgerJournalStatusEnum.POSTED, journal.getStatus());
    }

    /**
     * 借贷不等时过账服务应抛 LEDGER_NOT_BALANCED。
     * 不变量违反时服务不应继续推进状态。
     */
    @Test
    @DisplayName("借贷不等抛 LEDGER_NOT_BALANCED")
    void postJournal_withUnbalanced_throwsNotBalanced() {
        LedgerJournalEntity journal = LedgerJournalEntity.create(
                "SVC-002", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                List.of(
                        new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                new BigDecimal("100"), null, null, null, null),
                        new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                                new BigDecimal("99"), null, null, null, null)),
                null, null, null, null);
        assertThrows(BizException.class, () -> service.postJournal(journal));
        assertEquals(LedgerJournalStatusEnum.DRAFT, journal.getStatus());
    }

    /**
     * 同户同向重复时过账服务应抛 LEDGER_DUPLICATE_ACCOUNT。
     * 不变量违反时服务不应继续推进状态。
     */
    @Test
    @DisplayName("同户同向重复抛 LEDGER_DUPLICATE_ACCOUNT")
    void postJournal_withDuplicateAccount_throwsDuplicateAccount() {
        LedgerJournalEntity journal = LedgerJournalEntity.create(
                "SVC-003", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                List.of(
                        new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                new BigDecimal("100"), null, null, null, null),
                        new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                new BigDecimal("50"), null, null, null, null),
                        new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                                new BigDecimal("150"), null, null, null, null)),
                null, null, null, null);
        assertThrows(BizException.class, () -> service.postJournal(journal));
        assertEquals(LedgerJournalStatusEnum.DRAFT, journal.getStatus());
    }
}
