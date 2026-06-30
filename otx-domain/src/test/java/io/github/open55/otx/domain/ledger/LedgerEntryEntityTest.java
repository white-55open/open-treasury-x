package io.github.open55.otx.domain.ledger;

import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LedgerEntryEntity 不可变值对象单元测试。
 * <p>
 * 覆盖构造期 self-validate 的三类核心校验：accountCode 合法性、entryType 合法性、amount 正数；
 * 另验证字段透传、BaseEntity 继承带来的审计字段可访问。
 * <p>
 * Unit tests for the {@link LedgerEntryEntity} immutable value object.
 * <p>
 * Covers three core constructor self-validations (accountCode legality, entryType legality,
 * positive amount), plus field delegation and BaseEntity-inherited audit field accessibility.
 */
class LedgerEntryEntityTest {

    @Test
    void construct_withValidArgs_exposesAllFields() {
        /**
         * 场景：传入全部合法参数，应正确透传所有业务字段。
         * Scenario: All business fields are exposed verbatim when valid args are provided.
         */
        LedgerEntryEntity entry = new LedgerEntryEntity(
                "USER_AVAILABLE",
                LedgerEntryTypeEnum.DEBIT,
                new BigDecimal("100"),
                12345L,
                "0xabc",
                new BigDecimal("900"),
                "test remark");

        assertEquals("USER_AVAILABLE", entry.getAccountCode());
        assertSame(LedgerEntryTypeEnum.DEBIT, entry.getEntryType());
        assertEquals(new BigDecimal("100"), entry.getAmount());
        assertEquals(12345L, entry.getUid());
        assertEquals("0xabc", entry.getCounterparty());
        assertEquals(new BigDecimal("900"), entry.getBalanceAfter());
        assertEquals("test remark", entry.getRemark());
    }

    @Test
    void construct_withZeroAmount_throwsAmountInvalid() {
        /**
         * 场景：amount = 0 违反正数约束，应抛 LEDGER_AMOUNT_INVALID。
         * Scenario: amount = 0 violates the positive-amount invariant and must throw LEDGER_AMOUNT_INVALID.
         */
        BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                "USER_AVAILABLE",
                LedgerEntryTypeEnum.DEBIT,
                BigDecimal.ZERO,
                12345L,
                null,
                null,
                null));
        assertEquals("LEDGER_AMOUNT_INVALID", ex.getErrorCode());
    }

    @Test
    void construct_withNegativeAmount_throwsAmountInvalid() {
        /**
         * 场景：amount = -1 违反正数约束，应抛 LEDGER_AMOUNT_INVALID。
         * Scenario: amount = -1 violates the positive-amount invariant and must throw LEDGER_AMOUNT_INVALID.
         */
        BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                "USER_AVAILABLE",
                LedgerEntryTypeEnum.DEBIT,
                new BigDecimal("-1"),
                12345L,
                null,
                null,
                null));
        assertEquals("LEDGER_AMOUNT_INVALID", ex.getErrorCode());
    }

    @Test
    void construct_withNullAmount_throwsAmountInvalid() {
        /**
         * 场景：amount = null 视为非法，应抛 LEDGER_AMOUNT_INVALID（先于 entryType/accountCode 校验）。
         * Scenario: amount = null is treated as illegal and must throw LEDGER_AMOUNT_INVALID.
         */
        BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                "USER_AVAILABLE",
                LedgerEntryTypeEnum.DEBIT,
                null,
                12345L,
                null,
                null,
                null));
        assertEquals("LEDGER_AMOUNT_INVALID", ex.getErrorCode());
    }

    @Test
    void construct_withInvalidAccountCode_throwsAccountCodeInvalid() {
        /**
         * 场景：accountCode 不在 LedgerAccountCodeEnum 枚举集合内，应抛 LEDGER_ACCOUNT_CODE_INVALID。
         * Scenario: accountCode outside the LedgerAccountCodeEnum enum set must throw LEDGER_ACCOUNT_CODE_INVALID.
         */
        BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                "BOGUS_ACCOUNT",
                LedgerEntryTypeEnum.DEBIT,
                new BigDecimal("100"),
                12345L,
                null,
                null,
                null));
        assertEquals("LEDGER_ACCOUNT_CODE_INVALID", ex.getErrorCode());
    }

    @Test
    void construct_withNullAccountCode_throwsAccountCodeInvalid() {
        /**
         * 场景：accountCode = null 应抛 LEDGER_ACCOUNT_CODE_INVALID（最先校验）。
         * Scenario: accountCode = null must throw LEDGER_ACCOUNT_CODE_INVALID as the first check.
         */
        BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                null,
                LedgerEntryTypeEnum.DEBIT,
                new BigDecimal("100"),
                12345L,
                null,
                null,
                null));
        assertEquals("LEDGER_ACCOUNT_CODE_INVALID", ex.getErrorCode());
    }

    @Test
    void construct_withNullEntryType_throwsEntryTypeInvalid() {
        /**
         * 场景：entryType = null 应抛 LEDGER_ENTRY_TYPE_INVALID。
         * Scenario: entryType = null must throw LEDGER_ENTRY_TYPE_INVALID.
         */
        BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                "USER_AVAILABLE",
                null,
                new BigDecimal("100"),
                12345L,
                null,
                null,
                null));
        assertEquals("LEDGER_ENTRY_TYPE_INVALID", ex.getErrorCode());
    }

    @Test
    void construct_withCreditEntryType_succeeds() {
        /**
         * 场景：CREDIT 方向 + 可选字段（uid/counterparty/balanceAfter/remark）全为 null 时应成功构造。
         * Scenario: Construction succeeds with CREDIT direction and all optional fields set to null.
         */
        LedgerEntryEntity entry = new LedgerEntryEntity(
                "PLATFORM_HOT",
                LedgerEntryTypeEnum.CREDIT,
                new BigDecimal("100"),
                null,
                null,
                null,
                null);
        assertSame(LedgerEntryTypeEnum.CREDIT, entry.getEntryType());
        assertNull(entry.getUid());
        assertNotNull(entry.getAmount());
    }

    @Test
    void extendsBaseEntity_inheritsAuditFields() {
        /**
         * 场景：BaseEntity 的 id/version/tenantId 字段可被 set/get（基础设施字段由框架在持久化时回填）。
         * Scenario: BaseEntity id/version/tenantId fields are settable/gettable as infrastructure metadata.
         */
        LedgerEntryEntity entry = new LedgerEntryEntity(
                "USER_AVAILABLE",
                LedgerEntryTypeEnum.DEBIT,
                new BigDecimal("100"),
                12345L,
                null,
                null,
                null);
        entry.setId(1L);
        entry.setVersion(0L);
        entry.setTenantId("tenant-1");
        org.junit.jupiter.api.Assertions.assertEquals(1L, entry.getId());
        org.junit.jupiter.api.Assertions.assertEquals(0L, entry.getVersion());
        org.junit.jupiter.api.Assertions.assertEquals("tenant-1", entry.getTenantId());
    }
}
