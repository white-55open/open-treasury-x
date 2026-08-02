package io.github.open55.otx.domain.chain.port;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SignedTx 已签名交易值对象单元测试。
 * <p>
 * 覆盖构造期 self-validate 的 rawTransaction 非空校验，以及合法入参下所有字段的透传。
 * <p>
 * Unit tests for the {@link SignedTx} value object.
 * <p>
 * Covers constructor self-validation for non-blank rawTransaction and field delegation
 * with valid arguments.
 */
@DisplayName("SignedTx 已签名交易值对象单元测试 | SignedTx value object unit tests")
class SignedTxTest {

    private static final String CHAIN_ID = "11155111";
    private static final String RAW_TX = "0xf86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83";
    private static final String FROM_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String TO_ADDRESS = "0x2222222222222222222222222222222222222222";

    @Nested
    @DisplayName("构造期 self-validate 校验 | Constructor self-validation")
    class ConstructionValidation {

        /**
         * 场景：rawTransaction 为空白违反非空约束，应抛 IllegalArgumentException。
         * Scenario: blank rawTransaction violates the non-blank invariant and must throw IllegalArgumentException.
         */
        @Test
        @DisplayName("rawTransaction 为空白抛 IllegalArgumentException | Blank rawTransaction throws IllegalArgumentException")
        void construct_withBlankRawTransaction_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SignedTx(CHAIN_ID, "   ", FROM_ADDRESS, TO_ADDRESS));
        }

        /**
         * 场景：rawTransaction 为 null 违反非空约束，应抛 IllegalArgumentException。
         * Scenario: null rawTransaction violates the non-blank invariant and must throw IllegalArgumentException.
         */
        @Test
        @DisplayName("rawTransaction=null 抛 IllegalArgumentException | Null rawTransaction throws IllegalArgumentException")
        void construct_withNullRawTransaction_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SignedTx(CHAIN_ID, null, FROM_ADDRESS, TO_ADDRESS));
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
            SignedTx signedTx = new SignedTx(CHAIN_ID, RAW_TX, FROM_ADDRESS, TO_ADDRESS);

            assertEquals(CHAIN_ID, signedTx.getChainId());
            assertEquals(RAW_TX, signedTx.getRawTransaction());
            assertEquals(FROM_ADDRESS, signedTx.getFromAddress());
            assertEquals(TO_ADDRESS, signedTx.getToAddress());
        }
    }
}
