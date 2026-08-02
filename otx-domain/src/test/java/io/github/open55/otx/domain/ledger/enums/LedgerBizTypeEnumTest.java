package io.github.open55.otx.domain.ledger.enums;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LedgerBizTypeEnum 总账业务类型枚举单元测试。
 * <p>
 * 覆盖枚举值的存在性（新增 FREEZE / UNFREEZE 与既有 6 个值回归）、
 * 每个值的 code 与字面量一致性，以及全枚举 code 唯一性契约。
 * <p>
 * LedgerBizTypeEnum unit tests covering presence of enum values
 * (new FREEZE / UNFREEZE and regression of the legacy 6 values),
 * code-to-literal consistency for every value, and code uniqueness across the whole enum.
 */
@DisplayName("LedgerBizTypeEnum 总账业务类型枚举单元测试 | LedgerBizTypeEnum unit tests")
class LedgerBizTypeEnumTest {

    @Nested
    @DisplayName("新增冻结与解冻业务类型 | new freeze and unfreeze biz types")
    class NewBizTypes {

        /**
         * 场景：枚举包含 FREEZE 与 UNFREEZE 两个新增值，且各自 code 与字面量一致。
         * Scenario: the enum contains FREEZE and UNFREEZE with code matching their literal names.
         */
        @Test
        @DisplayName("包含 FREEZE 与 UNFREEZE 且 code 正确 | contains FREEZE and UNFREEZE with correct code")
        void enum_containsFreezeAndUnfreeze() {
            assertEquals("FREEZE", LedgerBizTypeEnum.FREEZE.getCode());
            assertEquals("UNFREEZE", LedgerBizTypeEnum.UNFREEZE.getCode());
        }
    }

    @Nested
    @DisplayName("既有六种业务类型回归 | legacy six biz types regression")
    class LegacyBizTypes {

        /**
         * 场景：既有 6 个枚举值仍然存在，且各自 code 与字面量一致。
         * Scenario: the legacy 6 enum values still exist with code matching their literal names.
         */
        @Test
        @DisplayName("既有 6 个枚举值存在且 code 正确 | legacy 6 values exist with correct code")
        void enum_legacySixValues_existWithCorrectCode() {
            assertEquals("DEPOSIT_ONCHAIN", LedgerBizTypeEnum.DEPOSIT_ONCHAIN.getCode());
            assertEquals("WITHDRAW_ONCHAIN", LedgerBizTypeEnum.WITHDRAW_ONCHAIN.getCode());
            assertEquals("INTERNAL_TRANSFER", LedgerBizTypeEnum.INTERNAL_TRANSFER.getCode());
            assertEquals("FEE", LedgerBizTypeEnum.FEE.getCode());
            assertEquals("REVERSAL", LedgerBizTypeEnum.REVERSAL.getCode());
            assertEquals("ADJUSTMENT", LedgerBizTypeEnum.ADJUSTMENT.getCode());
        }
    }

    @Nested
    @DisplayName("code 契约 | code contract")
    class CodeContract {

        /**
         * 场景：每个枚举值的 code 均与枚举字面量（name）一致。
         * Scenario: every enum value's code equals its literal name.
         */
        @Test
        @DisplayName("每个枚举值 code 与字面量一致 | code equals literal for every value")
        void enum_codeMatchesLiteral_forEachValue() {
            for (LedgerBizTypeEnum bizType : LedgerBizTypeEnum.values()) {
                assertEquals(bizType.name(), bizType.getCode());
            }
        }

        /**
         * 场景：全枚举 code 唯一，不存在重复值。
         * Scenario: all codes are unique across the whole enum.
         */
        @Test
        @DisplayName("全枚举 code 唯一 | all codes are unique")
        void enum_codes_areUnique() {
            Set<String> codes = new HashSet<>();
            for (LedgerBizTypeEnum bizType : LedgerBizTypeEnum.values()) {
                codes.add(bizType.getCode());
            }
            assertEquals(LedgerBizTypeEnum.values().length, codes.size());
        }
    }
}
