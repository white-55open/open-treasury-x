package io.github.open55.otx.domain.account.entity;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.open55.otx.common.exception.BizException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AccountEntity 账户实体单元测试。
 * <p>
 * 覆盖工厂方法 create、领域方法（freezeBalance / unfreezeBalance / withdraw / increaseBalance / deposit）
 * 的正常路径和所有错误码分支，以及 BaseEntity 继承字段的透传。
 */
@DisplayName("AccountEntity 账户实体单元测试 | AccountEntity unit tests")
class AccountEntityTest {

    private static final long TEST_UID = 12345L;
    private static final BigDecimal BALANCE_1000 = new BigDecimal("1000");
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final BigDecimal AMOUNT_200 = new BigDecimal("200");
    private static final BigDecimal AMOUNT_ZERO = BigDecimal.ZERO;
    private static final BigDecimal AMOUNT_NEG_1 = new BigDecimal("-1");
    private static final BigDecimal AMOUNT_50 = new BigDecimal("50");
    private static final BigDecimal FROZEN_30 = new BigDecimal("30");
    private static final String ERR_WITHDRAW_AMOUNT_INVALID = "WITHDRAW_AMOUNT_INVALID";
    private static final String ERR_INSUFFICIENT_FROZEN_BALANCE = "INSUFFICIENT_FROZEN_BALANCE";

    private static AccountEntity createDefaultAccount() {
        return AccountEntity.builder()
                .uid(TEST_UID)
                .availableBalance(BALANCE_1000)
                .frozenBalance(AMOUNT_ZERO)
                .build();
    }

    @Nested
    @DisplayName("create 工厂方法 | factory method")
    class CreateMethod {

        /**
         * 调用 create(uid) 应返回 uid 正确、余额为零的新账户。
         */
        @Test
        @DisplayName("创建新账户成功")
        void create_withValidUid_succeeds() {
            AccountEntity entity = AccountEntity.create(TEST_UID);
            assertEquals(TEST_UID, entity.getUid());
            assertNull(entity.getAvailableBalance());
            assertNull(entity.getFrozenBalance());
        }
    }

    @Nested
    @DisplayName("freezeBalance 冻结余额 | freeze balance")
    class FreezeBalance {

        /**
         * 正常冻结：可用余额从 1000 减少到 900，冻结从 0 增加到 100，总额守恒。
         */
        @Test
        @DisplayName("正常冻结成功")
        void freezeBalance_withSufficientBalance_succeeds() {
            AccountEntity entity = createDefaultAccount();
            entity.freezeBalance(AMOUNT_100);
            assertEquals(new BigDecimal("900"), entity.getAvailableBalance());
            assertEquals(AMOUNT_100, entity.getFrozenBalance());
        }

        /**
         * 冻结金额超过可用余额时抛 INSUFFICIENT_BALANCE，余额不变。
         */
        @Test
        @DisplayName("冻结金额超过可用余额抛 INSUFFICIENT_BALANCE")
        void freezeBalance_withInsufficientBalance_throwsInsufficient() {
            AccountEntity entity = createDefaultAccount();
            BizException ex = assertThrows(BizException.class, () -> entity.freezeBalance(new BigDecimal("1001")));
            assertEquals("INSUFFICIENT_BALANCE", ex.getErrorCode());
            assertEquals(BALANCE_1000, entity.getAvailableBalance());
            assertEquals(AMOUNT_ZERO, entity.getFrozenBalance());
        }

        /**
         * amount 为 null 时抛 FREEZE_AMOUNT_INVALID。
         */
        @Test
        @DisplayName("amount=null 抛 FREEZE_AMOUNT_INVALID")
        void freezeBalance_withNullAmount_throwsInvalid() {
            AccountEntity entity = createDefaultAccount();
            BizException ex = assertThrows(BizException.class, () -> entity.freezeBalance(null));
            assertEquals("FREEZE_AMOUNT_INVALID", ex.getErrorCode());
        }

        /**
         * amount 为 0 时抛 FREEZE_AMOUNT_INVALID。
         */
        @Test
        @DisplayName("amount=0 抛 FREEZE_AMOUNT_INVALID")
        void freezeBalance_withZeroAmount_throwsInvalid() {
            AccountEntity entity = createDefaultAccount();
            BizException ex = assertThrows(BizException.class, () -> entity.freezeBalance(AMOUNT_ZERO));
            assertEquals("FREEZE_AMOUNT_INVALID", ex.getErrorCode());
        }

        /**
         * amount 为负数时抛 FREEZE_AMOUNT_INVALID。
         */
        @Test
        @DisplayName("amount=-1 抛 FREEZE_AMOUNT_INVALID")
        void freezeBalance_withNegativeAmount_throwsInvalid() {
            AccountEntity entity = createDefaultAccount();
            BizException ex = assertThrows(BizException.class, () -> entity.freezeBalance(AMOUNT_NEG_1));
            assertEquals("FREEZE_AMOUNT_INVALID", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("unfreezeBalance 解冻余额 | unfreeze balance")
    class UnfreezeBalance {

        private static AccountEntity createFrozenAccount() {
            return AccountEntity.builder()
                    .uid(TEST_UID)
                    .availableBalance(new BigDecimal("900"))
                    .frozenBalance(AMOUNT_100)
                    .build();
        }

        /**
         * 正常解冻：冻结从 100 减少到 0，可用从 900 增加到 1000，总额守恒。
         */
        @Test
        @DisplayName("正常解冻成功")
        void unfreezeBalance_withSufficientFrozen_succeeds() {
            AccountEntity entity = createFrozenAccount();
            entity.unfreezeBalance(AMOUNT_100);
            assertEquals(BALANCE_1000, entity.getAvailableBalance());
            assertEquals(AMOUNT_ZERO, entity.getFrozenBalance());
        }

        /**
         * 解冻金额超过冻结余额时抛 INSUFFICIENT_FROZEN_BALANCE。
         */
        @Test
        @DisplayName("解冻金额超过冻结余额抛 INSUFFICIENT_FROZEN_BALANCE")
        void unfreezeBalance_withInsufficientFrozen_throwsInsufficient() {
            AccountEntity entity = createFrozenAccount();
            BizException ex = assertThrows(BizException.class, () -> entity.unfreezeBalance(AMOUNT_200));
            assertEquals("INSUFFICIENT_FROZEN_BALANCE", ex.getErrorCode());
            assertEquals(new BigDecimal("900"), entity.getAvailableBalance());
            assertEquals(AMOUNT_100, entity.getFrozenBalance());
        }

        /**
         * amount 为 null 时抛 UNFREEZE_AMOUNT_INVALID。
         */
        @Test
        @DisplayName("amount=null 抛 UNFREEZE_AMOUNT_INVALID")
        void unfreezeBalance_withNullAmount_throwsInvalid() {
            AccountEntity entity = createFrozenAccount();
            BizException ex = assertThrows(BizException.class, () -> entity.unfreezeBalance(null));
            assertEquals("UNFREEZE_AMOUNT_INVALID", ex.getErrorCode());
        }

        /**
         * amount 为 0 时抛 UNFREEZE_AMOUNT_INVALID。
         */
        @Test
        @DisplayName("amount=0 抛 UNFREEZE_AMOUNT_INVALID")
        void unfreezeBalance_withZeroAmount_throwsInvalid() {
            AccountEntity entity = createFrozenAccount();
            BizException ex = assertThrows(BizException.class, () -> entity.unfreezeBalance(AMOUNT_ZERO));
            assertEquals("UNFREEZE_AMOUNT_INVALID", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("withdraw 提现扣减 | withdraw")
    class Withdraw {

        /**
         * 构造可用余额 1000、冻结余额 100 的账户，用于提现测试。
         */
        private static AccountEntity createWithWithdrawableBalance() {
            return AccountEntity.builder()
                    .uid(TEST_UID)
                    .availableBalance(BALANCE_1000)
                    .frozenBalance(AMOUNT_100)
                    .build();
        }

        /**
         * 场景：冻结余额充足时提现成功，冻结余额减少、可用余额不变。
         * Scenario: withdraw succeeds with sufficient frozen balance, frozen decreases and available stays unchanged.
         */
        @Test
        @DisplayName("冻结余额充足正常提现成功 | withdraw succeeds with sufficient frozen balance")
        void withdraw_withSufficientFrozen_succeeds() {
            AccountEntity entity = createWithWithdrawableBalance();

            entity.withdraw(new BigDecimal("95"));

            assertEquals(BALANCE_1000, entity.getAvailableBalance());
            assertEquals(new BigDecimal("5"), entity.getFrozenBalance());
        }

        /**
         * 场景：可用余额为零、冻结余额恰好覆盖提现金额时仍可提现，冻结归零、可用不变。
         * Scenario: withdraw succeeds with zero available balance as long as frozen balance covers the amount.
         */
        @Test
        @DisplayName("可用余额为零冻结充足仍可提现 | withdraw succeeds with zero available balance")
        void withdraw_availableZeroAndFrozenEnough_deductsFrozenOnly() {
            AccountEntity entity = AccountEntity.builder()
                    .uid(TEST_UID)
                    .availableBalance(AMOUNT_ZERO)
                    .frozenBalance(AMOUNT_50)
                    .build();

            entity.withdraw(AMOUNT_50);

            assertEquals(AMOUNT_ZERO, entity.getAvailableBalance());
            assertEquals(AMOUNT_ZERO, entity.getFrozenBalance());
        }

        /**
         * 场景：提现金额恰好等于冻结余额（== 边界）时允许全额结算，冻结归零、可用不变。
         * Scenario: withdraw exactly equal to frozen balance is allowed and deducts all frozen funds.
         */
        @Test
        @DisplayName("提现金额等于冻结余额允许全额结算 | withdraw equal to frozen balance deducts all")
        void withdraw_amountEqualsFrozenBalance_deductsAll() {
            AccountEntity entity = createWithWithdrawableBalance();

            entity.withdraw(AMOUNT_100);

            assertEquals(BALANCE_1000, entity.getAvailableBalance());
            assertEquals(AMOUNT_ZERO, entity.getFrozenBalance());
        }

        /**
         * 场景：提现金额超过冻结余额时抛 BizException（INSUFFICIENT_FROZEN_BALANCE）而非裸 IllegalArgumentException，余额不变。
         * Scenario: withdraw exceeding frozen balance throws INSUFFICIENT_FROZEN_BALANCE BizException and leaves balances untouched.
         */
        @Test
        @DisplayName("提现金额超过冻结余额抛 INSUFFICIENT_FROZEN_BALANCE | withdraw exceeding frozen balance throws insufficient frozen balance")
        void withdraw_amountExceedsFrozenBalance_throwsInsufficientFrozenBalance() {
            AccountEntity entity = AccountEntity.builder()
                    .uid(TEST_UID)
                    .availableBalance(BALANCE_1000)
                    .frozenBalance(FROZEN_30)
                    .build();

            BizException ex = assertThrows(BizException.class, () -> entity.withdraw(AMOUNT_50));

            assertEquals(ERR_INSUFFICIENT_FROZEN_BALANCE, ex.getErrorCode());
            assertEquals(BALANCE_1000, entity.getAvailableBalance());
            assertEquals(FROZEN_30, entity.getFrozenBalance());
        }

        /**
         * 场景：amount 为 null 时抛 WITHDRAW_AMOUNT_INVALID。
         * Scenario: null amount throws WITHDRAW_AMOUNT_INVALID.
         */
        @Test
        @DisplayName("amount=null 抛 WITHDRAW_AMOUNT_INVALID | null amount throws WITHDRAW_AMOUNT_INVALID")
        void withdraw_nullAmount_throwsWithdrawAmountInvalid() {
            AccountEntity entity = createWithWithdrawableBalance();

            BizException ex = assertThrows(BizException.class, () -> entity.withdraw(null));

            assertEquals(ERR_WITHDRAW_AMOUNT_INVALID, ex.getErrorCode());
        }

        /**
         * 场景：amount 为非正数（0 或负数）时抛 WITHDRAW_AMOUNT_INVALID。
         * Scenario: non-positive amount (zero or negative) throws WITHDRAW_AMOUNT_INVALID.
         */
        @ParameterizedTest
        @ValueSource(strings = {"0", "-1"})
        @DisplayName("amount=0 或负数抛 WITHDRAW_AMOUNT_INVALID | zero or negative amount throws WITHDRAW_AMOUNT_INVALID")
        void withdraw_nonPositiveAmount_throwsWithdrawAmountInvalid(String amountText) {
            AccountEntity entity = createWithWithdrawableBalance();

            BizException ex = assertThrows(BizException.class, () -> entity.withdraw(new BigDecimal(amountText)));

            assertEquals(ERR_WITHDRAW_AMOUNT_INVALID, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("increaseBalance 增加可用余额 | increase balance")
    class IncreaseBalance {

        /**
         * 增加可用余额后 availableBalance 正确增加。
         */
        @Test
        @DisplayName("增加可用余额成功")
        void increaseBalance_withPositiveAmount_succeeds() {
            AccountEntity entity = createDefaultAccount();
            entity.increaseBalance(AMOUNT_100);
            assertEquals(new BigDecimal("1100"), entity.getAvailableBalance());
            assertEquals(AMOUNT_ZERO, entity.getFrozenBalance());
        }
    }

    @Nested
    @DisplayName("deposit 充值入口 | deposit")
    class Deposit {

        /**
         * deposit 委托给 increaseBalance，可用余额正确增加。
         */
        @Test
        @DisplayName("充值成功")
        void deposit_withPositiveAmount_succeeds() {
            AccountEntity entity = createDefaultAccount();
            entity.deposit(AMOUNT_200);
            assertEquals(new BigDecimal("1200"), entity.getAvailableBalance());
        }
    }

    @Nested
    @DisplayName("BaseEntity 继承字段 | inherited BaseEntity fields")
    class BaseEntityInheritance {

        /**
         * BaseEntity 的 id/version/tenantId 字段通过继承可正常 set/get。
         */
        @Test
        @DisplayName("id/version/tenantId 可被 set/get")
        void baseEntityFields_areAccessible() {
            AccountEntity entity = AccountEntity.create(TEST_UID);
            entity.setId(1L);
            entity.setVersion(0L);
            entity.setTenantId("tenant-1");
            assertEquals(1L, entity.getId());
            assertEquals(0L, entity.getVersion());
            assertEquals("tenant-1", entity.getTenantId());
        }
    }
}
