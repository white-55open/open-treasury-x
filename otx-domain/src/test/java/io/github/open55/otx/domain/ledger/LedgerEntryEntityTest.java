package io.github.open55.otx.domain.ledger;

import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("LedgerEntryEntity 不可变值对象单元测试 | LedgerEntryEntity immutable value object unit tests")
class LedgerEntryEntityTest {

    private static final String ACCOUNT_USER_AVAILABLE = "USER_AVAILABLE";
    private static final String ACCOUNT_PLATFORM_HOT = "PLATFORM_HOT";
    private static final String ACCOUNT_BOGUS = "BOGUS_ACCOUNT";

    private static final long TEST_UID = 12345L;

    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final BigDecimal AMOUNT_900 = new BigDecimal("900");
    private static final BigDecimal AMOUNT_NEG_1 = new BigDecimal("-1");

    private static final String ERR_AMOUNT_INVALID = "LEDGER_AMOUNT_INVALID";
    private static final String ERR_ACCOUNT_CODE_INVALID = "LEDGER_ACCOUNT_CODE_INVALID";
    private static final String ERR_ENTRY_TYPE_INVALID = "LEDGER_ENTRY_TYPE_INVALID";

    private static final long INFRA_ID = 1L;
    private static final long INFRA_VERSION = 0L;
    private static final String INFRA_TENANT = "tenant-1";

    @Nested
    @DisplayName("构造期 self-validate 校验 | Constructor self-validation")
    class ConstructionValidation {

        /**
         * 场景：amount = 0 违反正数约束，应抛 LEDGER_AMOUNT_INVALID。
         * Scenario: amount = 0 violates the positive-amount invariant and must throw LEDGER_AMOUNT_INVALID.
         */
        @Test
        @DisplayName("amount=0 抛 LEDGER_AMOUNT_INVALID | Zero amount throws LEDGER_AMOUNT_INVALID")
        void construct_withZeroAmount_throwsAmountInvalid() {
            BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                    ACCOUNT_USER_AVAILABLE,
                    LedgerEntryTypeEnum.DEBIT,
                    BigDecimal.ZERO,
                    TEST_UID,
                    null,
                    null,
                    null));
            assertEquals(ERR_AMOUNT_INVALID, ex.getErrorCode());
        }

        /**
         * 场景：amount = -1 违反正数约束，应抛 LEDGER_AMOUNT_INVALID。
         * Scenario: amount = -1 violates the positive-amount invariant and must throw LEDGER_AMOUNT_INVALID.
         */
        @Test
        @DisplayName("amount=-1 抛 LEDGER_AMOUNT_INVALID | Negative amount throws LEDGER_AMOUNT_INVALID")
        void construct_withNegativeAmount_throwsAmountInvalid() {
            BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                    ACCOUNT_USER_AVAILABLE,
                    LedgerEntryTypeEnum.DEBIT,
                    AMOUNT_NEG_1,
                    TEST_UID,
                    null,
                    null,
                    null));
            assertEquals(ERR_AMOUNT_INVALID, ex.getErrorCode());
        }

        /**
         * 场景：amount = null 视为非法，应抛 LEDGER_AMOUNT_INVALID（先于 entryType/accountCode 校验）。
         * Scenario: amount = null is treated as illegal and must throw LEDGER_AMOUNT_INVALID.
         */
        @Test
        @DisplayName("amount=null 抛 LEDGER_AMOUNT_INVALID | Null amount throws LEDGER_AMOUNT_INVALID")
        void construct_withNullAmount_throwsAmountInvalid() {
            BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                    ACCOUNT_USER_AVAILABLE,
                    LedgerEntryTypeEnum.DEBIT,
                    null,
                    TEST_UID,
                    null,
                    null,
                    null));
            assertEquals(ERR_AMOUNT_INVALID, ex.getErrorCode());
        }

        /**
         * 场景：accountCode 不在 LedgerAccountCodeEnum 枚举集合内，应抛 LEDGER_ACCOUNT_CODE_INVALID。
         * Scenario: accountCode outside the LedgerAccountCodeEnum enum set must throw LEDGER_ACCOUNT_CODE_INVALID.
         */
        @Test
        @DisplayName("未知 accountCode 抛 LEDGER_ACCOUNT_CODE_INVALID | Unknown accountCode throws LEDGER_ACCOUNT_CODE_INVALID")
        void construct_withInvalidAccountCode_throwsAccountCodeInvalid() {
            BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                    ACCOUNT_BOGUS,
                    LedgerEntryTypeEnum.DEBIT,
                    AMOUNT_100,
                    TEST_UID,
                    null,
                    null,
                    null));
            assertEquals(ERR_ACCOUNT_CODE_INVALID, ex.getErrorCode());
        }

        /**
         * 场景：accountCode = null 应抛 LEDGER_ACCOUNT_CODE_INVALID（最先校验）。
         * Scenario: accountCode = null must throw LEDGER_ACCOUNT_CODE_INVALID as the first check.
         */
        @Test
        @DisplayName("accountCode=null 抛 LEDGER_ACCOUNT_CODE_INVALID | Null accountCode throws LEDGER_ACCOUNT_CODE_INVALID")
        void construct_withNullAccountCode_throwsAccountCodeInvalid() {
            BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                    null,
                    LedgerEntryTypeEnum.DEBIT,
                    AMOUNT_100,
                    TEST_UID,
                    null,
                    null,
                    null));
            assertEquals(ERR_ACCOUNT_CODE_INVALID, ex.getErrorCode());
        }

        /**
         * 场景：entryType = null 应抛 LEDGER_ENTRY_TYPE_INVALID。
         * Scenario: entryType = null must throw LEDGER_ENTRY_TYPE_INVALID.
         */
        @Test
        @DisplayName("entryType=null 抛 LEDGER_ENTRY_TYPE_INVALID | Null entryType throws LEDGER_ENTRY_TYPE_INVALID")
        void construct_withNullEntryType_throwsEntryTypeInvalid() {
            BizException ex = assertThrows(BizException.class, () -> new LedgerEntryEntity(
                    ACCOUNT_USER_AVAILABLE,
                    null,
                    AMOUNT_100,
                    TEST_UID,
                    null,
                    null,
                    null));
            assertEquals(ERR_ENTRY_TYPE_INVALID, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("字段透传与成功构造 | Field delegation and successful construction")
    class FieldDelegation {

        /**
         * 场景：传入全部合法参数，应正确透传所有业务字段。
         * Scenario: All business fields are exposed verbatim when valid args are provided.
         */
        @Test
        @DisplayName("合法参数下 7 个业务字段全部透传 | All 7 business fields are exposed verbatim with valid args")
        void construct_withValidArgs_exposesAllFields() {
            LedgerEntryEntity entry = new LedgerEntryEntity(
                    ACCOUNT_USER_AVAILABLE,
                    LedgerEntryTypeEnum.DEBIT,
                    AMOUNT_100,
                    TEST_UID,
                    "0xabc",
                    AMOUNT_900,
                    "test remark");

            assertEquals(ACCOUNT_USER_AVAILABLE, entry.getAccountCode());
            assertSame(LedgerEntryTypeEnum.DEBIT, entry.getEntryType());
            assertEquals(AMOUNT_100, entry.getAmount());
            assertEquals(TEST_UID, entry.getUid());
            assertEquals("0xabc", entry.getCounterparty());
            assertEquals(AMOUNT_900, entry.getBalanceAfter());
            assertEquals("test remark", entry.getRemark());
        }

        /**
         * 场景：CREDIT 方向 + 可选字段（uid/counterparty/balanceAfter/remark）全为 null 时应成功构造。
         * Scenario: Construction succeeds with CREDIT direction and all optional fields set to null.
         */
        @Test
        @DisplayName("CREDIT 方向 + 可选字段全 null 成功构造 | CREDIT direction with all optional fields null succeeds")
        void construct_withCreditEntryType_succeeds() {
            LedgerEntryEntity entry = new LedgerEntryEntity(
                    ACCOUNT_PLATFORM_HOT,
                    LedgerEntryTypeEnum.CREDIT,
                    AMOUNT_100,
                    null,
                    null,
                    null,
                    null);
            assertSame(LedgerEntryTypeEnum.CREDIT, entry.getEntryType());
            assertNull(entry.getUid());
            assertNotNull(entry.getAmount());
        }
    }

    @Nested
    @DisplayName("BaseEntity 继承语义 | BaseEntity inheritance")
    class BaseEntityInheritance {

        /**
         * 场景：BaseEntity 的 id/version/tenantId 字段可被 set/get（基础设施字段由框架在持久化时回填）。
         * Scenario: BaseEntity id/version/tenantId fields are settable/gettable as infrastructure metadata.
         */
        @Test
        @DisplayName("id/version/tenantId 可被 set/get | id/version/tenantId are settable/gettable")
        void extendsBaseEntity_inheritsAuditFields() {
            LedgerEntryEntity entry = new LedgerEntryEntity(
                    ACCOUNT_USER_AVAILABLE,
                    LedgerEntryTypeEnum.DEBIT,
                    AMOUNT_100,
                    TEST_UID,
                    null,
                    null,
                    null);
            entry.setId(INFRA_ID);
            entry.setVersion(INFRA_VERSION);
            entry.setTenantId(INFRA_TENANT);
            assertEquals(INFRA_ID, entry.getId());
            assertEquals(INFRA_VERSION, entry.getVersion());
            assertEquals(INFRA_TENANT, entry.getTenantId());
        }
    }
}
