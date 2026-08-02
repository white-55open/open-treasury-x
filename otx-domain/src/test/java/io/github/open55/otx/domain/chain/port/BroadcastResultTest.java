package io.github.open55.otx.domain.chain.port;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BroadcastResult 广播结果值对象单元测试。
 * <p>
 * 覆盖构造期 self-validate 的 txHash 非空校验，以及合法入参下所有字段的透传。
 * <p>
 * Unit tests for the {@link BroadcastResult} value object.
 * <p>
 * Covers constructor self-validation for non-blank txHash and field delegation
 * with valid arguments.
 */
@DisplayName("BroadcastResult 广播结果值对象单元测试 | BroadcastResult value object unit tests")
class BroadcastResultTest {

    private static final String CHAIN_ID = "11155111";
    private static final String TX_HASH = "0x9fc76417374aa880d4449a1f7f31ec597f00b1f6f3dd2d258f6e3a5f4c1f5e2a";
    private static final String FROM_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String TO_ADDRESS = "0x2222222222222222222222222222222222222222";

    @Nested
    @DisplayName("构造期 self-validate 校验 | Constructor self-validation")
    class ConstructionValidation {

        /**
         * 场景：txHash 为空白违反非空约束，应抛 IllegalArgumentException。
         * Scenario: blank txHash violates the non-blank invariant and must throw IllegalArgumentException.
         */
        @Test
        @DisplayName("txHash 为空白抛 IllegalArgumentException | Blank txHash throws IllegalArgumentException")
        void construct_withBlankTxHash_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BroadcastResult(CHAIN_ID, " ", FROM_ADDRESS, TO_ADDRESS));
        }

        /**
         * 场景：txHash 为 null 违反非空约束，应抛 IllegalArgumentException。
         * Scenario: null txHash violates the non-blank invariant and must throw IllegalArgumentException.
         */
        @Test
        @DisplayName("txHash=null 抛 IllegalArgumentException | Null txHash throws IllegalArgumentException")
        void construct_withNullTxHash_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new BroadcastResult(CHAIN_ID, null, FROM_ADDRESS, TO_ADDRESS));
        }
    }

    @Nested
    @DisplayName("合法参数构造与字段透传 | Field delegation with valid arguments")
    class FieldDelegation {

        /**
         * 场景：传入全部合法参数，应正确透传所有业务字段。
         * Scenario: All business fields are exposed verbatim when valid args are provided.
         */
        @Test
        @DisplayName("合法参数下 4 个字段全部透传 | All 4 fields are exposed verbatim with valid args")
        void construct_withValidFields_succeeds() {
            BroadcastResult result = new BroadcastResult(CHAIN_ID, TX_HASH, FROM_ADDRESS, TO_ADDRESS);

            assertEquals(CHAIN_ID, result.getChainId());
            assertEquals(TX_HASH, result.getTxHash());
            assertEquals(FROM_ADDRESS, result.getFromAddress());
            assertEquals(TO_ADDRESS, result.getToAddress());
        }
    }
}
