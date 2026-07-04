package io.github.open55.otx.domain.ledger;

import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerJournalStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LedgerJournalEntity 聚合根单元测试。
 * <p>
 * 覆盖工厂方法 create() 的入参校验（bizNo/currency/entries 为空或 blank 均抛异常），
 * 合法入参正确透传所有字段，以及 entries 返回不可变列表的语义。
 */
@DisplayName("LedgerJournalEntity 聚合根单元测试 | aggregate root unit tests")
class LedgerJournalEntityTest {

    private static final String BIZ_NO = "DEP-20260101-0001";
    private static final LedgerBizTypeEnum BIZ_TYPE = LedgerBizTypeEnum.DEPOSIT_ONCHAIN;
    private static final String CURRENCY = "USDT";
    private static final LocalDate POSTING_DATE = LocalDate.of(2026, 1, 1);

    private static List<LedgerEntryEntity> createValidEntries() {
        return List.of(
                new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                        new BigDecimal("100"), 12345L, null, null, null),
                new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                        new BigDecimal("100"), 12345L, null, null, null)
        );
    }

    @Nested
    @DisplayName("assertBalanced 借贷平衡校验 | debit/credit balance validation")
    class AssertBalanced {

        /**
         * DEBIT 总额（100）不等于 CREDIT 总额（99）时校验应抛 LEDGER_NOT_BALANCED。
         * 复式记账的核心不变量：借贷必平。
         */
        @Test
        @DisplayName("借贷不等抛 LEDGER_NOT_BALANCED")
        void unbalancedAmounts_throwsNotBalanced() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "UNBAL-001", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    List.of(
                            new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                    new BigDecimal("100"), null, null, null, null),
                            new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                                    new BigDecimal("99"), null, null, null, null)),
                    null, null, null, null);
            BizException ex = assertThrows(BizException.class, journal::assertBalanced);
            assertEquals("LEDGER_NOT_BALANCED", ex.getErrorCode());
        }

        /**
         * entries 中只有 DEBIT 分录、缺少 CREDIT 分录时校验应抛 LEDGER_ENTRIES_EMPTY。
         * 复式记账必须至少各有一条 DEBIT 和 CREDIT。
         */
        @Test
        @DisplayName("缺 CREDIT 分录抛 LEDGER_ENTRIES_EMPTY")
        void onlyDebitEntries_throwsEntriesEmpty() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "NO-CREDIT-001", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    List.of(
                            new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                    new BigDecimal("100"), null, null, null, null)),
                    null, null, null, null);
            BizException ex = assertThrows(BizException.class, journal::assertBalanced);
            assertEquals("LEDGER_ENTRIES_EMPTY", ex.getErrorCode());
        }

        /**
         * entries 中只有 CREDIT 分录、缺少 DEBIT 分录时校验应抛 LEDGER_ENTRIES_EMPTY。
         * 复式记账必须至少各有一条 DEBIT 和 CREDIT。
         */
        @Test
        @DisplayName("缺 DEBIT 分录抛 LEDGER_ENTRIES_EMPTY")
        void onlyCreditEntries_throwsEntriesEmpty() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "NO-DEBIT-001", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    List.of(
                            new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                                    new BigDecimal("100"), null, null, null, null)),
                    null, null, null, null);
            BizException ex = assertThrows(BizException.class, journal::assertBalanced);
            assertEquals("LEDGER_ENTRIES_EMPTY", ex.getErrorCode());
        }

        /**
         * DEBIT 总额等于 CREDIT 总额时校验通过，不抛异常。
         * 两条分录（DEBIT 100 = CREDIT 100）满足借贷平衡。
         */
        @Test
        @DisplayName("借贷相等校验通过")
        void balancedAmounts_passes() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "BAL-001", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    createValidEntries(), null, null, null, null);
            assertDoesNotThrow(journal::assertBalanced);
        }
    }

    @Nested
    @DisplayName("assertNoDuplicateAccount 同户同向唯一校验 | duplicate account detection")
    class AssertNoDuplicateAccount {

        /**
         * 同账户在相同方向上出现两次时校验应抛 LEDGER_DUPLICATE_ACCOUNT。
         * 同一张凭证内不允许同一账户被重复借记或重复贷记。
         */
        @Test
        @DisplayName("同户同向重复抛 LEDGER_DUPLICATE_ACCOUNT")
        void duplicateAccountSameEntryType_throwsDuplicateAccount() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "DUP-001", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    List.of(
                            new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                    new BigDecimal("100"), null, null, null, null),
                            new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                    new BigDecimal("50"), null, null, null, null),
                            new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                                    new BigDecimal("150"), null, null, null, null)),
                    null, null, null, null);
            BizException ex = assertThrows(BizException.class, journal::assertNoDuplicateAccount);
            assertEquals("LEDGER_DUPLICATE_ACCOUNT", ex.getErrorCode());
        }

        /**
         * 同一账户 DEBIT 和 CREDIT 各出现一次时校验通过，不抛异常。
         * 同账户跨方向（一借一贷）是合法的复式记账场景。
         */
        @Test
        @DisplayName("同户异向（一借一贷）校验通过")
        void sameAccountDifferentEntryType_passes() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "DUP-002", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    List.of(
                            new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                    new BigDecimal("100"), null, null, null, null),
                            new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.CREDIT,
                                    new BigDecimal("50"), null, null, null, null),
                            new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                                    new BigDecimal("50"), null, null, null, null)),
                    null, null, null, null);
            assertDoesNotThrow(journal::assertNoDuplicateAccount);
        }
    }

    @Nested
    @DisplayName("post 状态转换 | state transition DRAFT → POSTED")
    class PostMethod {

        /**
         * DRAFT 状态的凭证调用 post() 后状态变为 POSTED。
         * 正常过账流程：校验 → 状态推进。
         */
        @Test
        @DisplayName("DRAFT 可过账为 POSTED")
        void draftJournal_post_succeeds() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "POST-001", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    createValidEntries(), null, null, null, null);
            journal.post();
            assertEquals(LedgerJournalStatusEnum.POSTED, journal.getStatus());
        }

        /**
         * POSTED 状态的凭证再次调用 post() 应抛 LEDGER_JOURNAL_NOT_DRAFT。
         * 防止重复过账导致账目翻倍。
         */
        @Test
        @DisplayName("POSTED 重复 post 抛 LEDGER_JOURNAL_NOT_DRAFT")
        void postedJournal_postAgain_throwsNotDraft() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "POST-002", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    createValidEntries(), null, null, null, null);
            journal.post();
            BizException ex = assertThrows(BizException.class, journal::post);
            assertEquals("LEDGER_JOURNAL_NOT_DRAFT", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("reverse 冲销 | reversal state transition POSTED → REVERSED")
    class ReverseMethod {

        /**
         * POSTED 状态的凭证调用 reverse() 后原凭证状态变为 REVERSED，
         * 且 reversedBy 被设置为反向凭证的 ID。
         * 冲销后原凭证不可再修改。
         */
        @Test
        @DisplayName("POSTED 可冲销为 REVERSED")
        void postedJournal_reverse_succeeds() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "REV-001", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    createValidEntries(), null, null, null, null);
            journal.post();
            journal.setId(100L);

            LedgerJournalEntity reason = LedgerJournalEntity.create(
                    "RV-REV-001", LedgerBizTypeEnum.REVERSAL, CURRENCY, POSTING_DATE,
                    "reversal", journal.createReversedEntries("RV-REV-001"),
                    null, null, null, null);
            reason.setId(200L);

            journal.reverse(reason);
            assertEquals(LedgerJournalStatusEnum.REVERSED, journal.getStatus());
            assertEquals(200L, journal.getReversedBy());
        }

        /**
         * REVERSED 状态的凭证再次调用 reverse() 应抛 LEDGER_JOURNAL_NOT_POSTED。
         * 防止重复冲销导致账目混乱。
         */
        @Test
        @DisplayName("REVERSED 重复 reverse 抛 LEDGER_JOURNAL_NOT_POSTED")
        void reversedJournal_reverseAgain_throwsNotPosted() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "REV-002", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    createValidEntries(), null, null, null, null);
            journal.post();
            journal.setId(100L);

            LedgerJournalEntity reason = LedgerJournalEntity.create(
                    "RV-REV-002", LedgerBizTypeEnum.REVERSAL, CURRENCY, POSTING_DATE,
                    null, journal.createReversedEntries("RV-REV-002"),
                    null, null, null, null);
            reason.setId(200L);

            journal.reverse(reason);
            BizException ex = assertThrows(BizException.class, () -> journal.reverse(reason));
            assertEquals("LEDGER_JOURNAL_NOT_POSTED", ex.getErrorCode());
        }

        /**
         * createReversedEntries 生成的分录中 DEBIT ↔ CREDIT 互换。
         * 原 DEBIT 分录的反向应为 CREDIT，原 CREDIT 分录的反向应为 DEBIT。
         */
        @Test
        @DisplayName("反向分录借贷方向互换")
        void createReversedEntries_swapsDebitCredit() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    "REV-003", BIZ_TYPE, CURRENCY, POSTING_DATE, null,
                    List.of(
                            new LedgerEntryEntity(LedgerAccountCodeEnum.PLATFORM_HOT, LedgerEntryTypeEnum.DEBIT,
                                    new BigDecimal("100"), null, null, null, null),
                            new LedgerEntryEntity(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, LedgerEntryTypeEnum.CREDIT,
                                    new BigDecimal("100"), null, null, null, null)),
                    null, null, null, null);

            List<LedgerEntryEntity> reversed = journal.createReversedEntries("RV-REV-003");
            assertEquals(2, reversed.size());
            // 原 DEBIT → CREDIT
            assertEquals(LedgerEntryTypeEnum.CREDIT, reversed.get(0).getEntryType());
            assertEquals(LedgerAccountCodeEnum.PLATFORM_HOT, reversed.get(0).getAccountCode());
            assertEquals(new BigDecimal("100"), reversed.get(0).getAmount());
            // 原 CREDIT → DEBIT
            assertEquals(LedgerEntryTypeEnum.DEBIT, reversed.get(1).getEntryType());
            assertEquals(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, reversed.get(1).getAccountCode());
            assertEquals(new BigDecimal("100"), reversed.get(1).getAmount());
        }
    }

    @Nested
    @DisplayName("create 工厂入参校验 | factory input validation")
    class CreateValidation {

        /**
         * bizNo 为 null 时创建凭证应抛 LEDGER_BIZ_NO_EMPTY 异常。
         * 业务幂等键是必填字段，缺少则拒绝创建。
         */
        @Test
        @DisplayName("bizNo=null 抛 LEDGER_BIZ_NO_EMPTY")
        void create_withNullBizNo_throwsBizNoEmpty() {
            BizException ex = assertThrows(BizException.class, () ->
                    LedgerJournalEntity.create(null, BIZ_TYPE, CURRENCY, POSTING_DATE,
                            null, createValidEntries(), null, null, null, null));
            assertEquals("LEDGER_BIZ_NO_EMPTY", ex.getErrorCode());
        }

        /**
         * bizNo 为空字符串时创建凭证应抛 LEDGER_BIZ_NO_EMPTY 异常。
         * 空白字符串同样视为无效的业务幂等键。
         */
        @Test
        @DisplayName("bizNo=blank 抛 LEDGER_BIZ_NO_EMPTY")
        void create_withBlankBizNo_throwsBizNoEmpty() {
            BizException ex = assertThrows(BizException.class, () ->
                    LedgerJournalEntity.create("", BIZ_TYPE, CURRENCY, POSTING_DATE,
                            null, createValidEntries(), null, null, null, null));
            assertEquals("LEDGER_BIZ_NO_EMPTY", ex.getErrorCode());
        }

        /**
         * currency 为 null 时创建凭证应抛 LEDGER_CURRENCY_EMPTY 异常。
         * 币种是必填字段，缺少则拒绝创建。
         */
        @Test
        @DisplayName("currency=null 抛 LEDGER_CURRENCY_EMPTY")
        void create_withNullCurrency_throwsCurrencyEmpty() {
            BizException ex = assertThrows(BizException.class, () ->
                    LedgerJournalEntity.create(BIZ_NO, BIZ_TYPE, null, POSTING_DATE,
                            null, createValidEntries(), null, null, null, null));
            assertEquals("LEDGER_CURRENCY_EMPTY", ex.getErrorCode());
        }

        /**
         * entries 为 null 时创建凭证应抛 LEDGER_ENTRIES_EMPTY 异常。
         * 复式记账必须至少包含一条分录，空 entries 无法满足借贷平衡要求。
         */
        @Test
        @DisplayName("entries=null 抛 LEDGER_ENTRIES_EMPTY")
        void create_withNullEntries_throwsEntriesEmpty() {
            BizException ex = assertThrows(BizException.class, () ->
                    LedgerJournalEntity.create(BIZ_NO, BIZ_TYPE, CURRENCY, POSTING_DATE,
                            null, null, null, null, null, null));
            assertEquals("LEDGER_ENTRIES_EMPTY", ex.getErrorCode());
        }

        /**
         * entries 为空列表时创建凭证应抛 LEDGER_ENTRIES_EMPTY 异常。
         * 空列表同样不满足复式记账至少各一条 DEBIT 和 CREDIT 的基本要求。
         */
        @Test
        @DisplayName("entries=empty 抛 LEDGER_ENTRIES_EMPTY")
        void create_withEmptyEntries_throwsEntriesEmpty() {
            BizException ex = assertThrows(BizException.class, () ->
                    LedgerJournalEntity.create(BIZ_NO, BIZ_TYPE, CURRENCY, POSTING_DATE,
                            null, List.of(), null, null, null, null));
            assertEquals("LEDGER_ENTRIES_EMPTY", ex.getErrorCode());
        }

        /**
         * 所有入参合法时创建凭证应成功，所有业务字段正确透传。
         * 验证 Web3 链上字段、状态默认 DRAFT、totalAmount 自动计算、entries 数量。
         */
        @Test
        @DisplayName("合法入参成功创建")
        void create_withValidArgs_succeeds() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    BIZ_NO, BIZ_TYPE, CURRENCY, POSTING_DATE,
                    "test deposit", createValidEntries(),
                    "1", "0xabc", 18000000L, "0xdac");
            assertEquals(BIZ_NO, journal.getBizNo());
            assertEquals(BIZ_TYPE, journal.getBizType());
            assertEquals(CURRENCY, journal.getCurrency());
            assertEquals(POSTING_DATE, journal.getPostingDate());
            assertEquals("test deposit", journal.getDescription());
            assertEquals("1", journal.getChainId());
            assertEquals("0xabc", journal.getChainTxHash());
            assertEquals(18000000L, journal.getBlockNumber());
            assertEquals("0xdac", journal.getTokenAddress());
            assertEquals(LedgerJournalStatusEnum.DRAFT, journal.getStatus());
            assertEquals(new BigDecimal("200"), journal.getTotalAmount());
            assertEquals(2, journal.getEntries().size());
        }

        /**
         * getEntries() 返回的列表不可修改，修改应抛 UnsupportedOperationException。
         * 通过不可变包装防止外部绕过聚合根直接篡改分录。
         */
        @Test
        @DisplayName("entries 返回不可变列表")
        void entriesList_isUnmodifiable() {
            LedgerJournalEntity journal = LedgerJournalEntity.create(
                    BIZ_NO, BIZ_TYPE, CURRENCY, POSTING_DATE,
                    null, createValidEntries(), null, null, null, null);
            assertThrows(UnsupportedOperationException.class, () ->
                    journal.getEntries().add(null));
        }
    }
}
