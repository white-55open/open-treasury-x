package io.github.open55.otx.domain.chain.port;

import java.math.BigInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SignRequest 签名请求值对象单元测试。
 * <p>
 * 覆盖构造期 self-validate 的必填字段校验（chainId/fromAddress/toAddress/amountWei 非空、
 * amountWei 必须为正），以及合法入参下所有字段的透传。
 * <p>
 * Unit tests for the {@link SignRequest} value object.
 * <p>
 * Covers constructor self-validations for required fields (non-blank chainId/fromAddress/
 * toAddress, non-null positive amountWei) and field delegation with valid arguments.
 */
@DisplayName("SignRequest 签名请求值对象单元测试 | SignRequest value object unit tests")
class SignRequestTest {

    private static final String CHAIN_ID = "11155111";
    private static final String FROM_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String TO_ADDRESS = "0x2222222222222222222222222222222222222222";
    private static final BigInteger AMOUNT_WEI_100 = BigInteger.valueOf(100);
    private static final BigInteger AMOUNT_WEI_NEG_1 = BigInteger.valueOf(-1);
    private static final String TOKEN_ADDRESS = "0x3333333333333333333333333333333333333333";
    private static final BigInteger NONCE_0 = BigInteger.ZERO;
    private static final BigInteger GAS_LIMIT_21000 = BigInteger.valueOf(21000);
    private static final String DATA_HEX = "0xabcdef";

    @Nested
    @DisplayName("构造期 self-validate 校验 | Constructor self-validation")
    class ConstructionValidation {

        /**
         * 场景：chainId 为 null 违反必填约束，应抛 IllegalArgumentException。
         * Scenario: null chainId violates the required-field invariant and must throw IllegalArgumentException.
         */
        @Test
        @DisplayName("chainId=null 抛 IllegalArgumentException | Null chainId throws IllegalArgumentException")
        void construct_withMissingChainId_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SignRequest(null, FROM_ADDRESS, TO_ADDRESS, AMOUNT_WEI_100,
                            null, null, null, null));
        }

        /**
         * 场景：amountWei 为负数违反正数约束，应抛 IllegalArgumentException。
         * Scenario: negative amountWei violates the positive-amount invariant and must throw IllegalArgumentException.
         */
        @Test
        @DisplayName("amountWei 为负数抛 IllegalArgumentException | Negative amountWei throws IllegalArgumentException")
        void construct_withNegativeAmountWei_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SignRequest(CHAIN_ID, FROM_ADDRESS, TO_ADDRESS, AMOUNT_WEI_NEG_1,
                            null, null, null, null));
        }

        /**
         * 场景：amountWei 为 null 违反必填约束，应抛 IllegalArgumentException。
         * Scenario: null amountWei violates the required-field invariant and must throw IllegalArgumentException.
         */
        @Test
        @DisplayName("amountWei=null 抛 IllegalArgumentException | Null amountWei throws IllegalArgumentException")
        void construct_withNullAmountWei_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SignRequest(CHAIN_ID, FROM_ADDRESS, TO_ADDRESS, null,
                            null, null, null, null));
        }

        /**
         * 场景：fromAddress 为空白违反必填约束，应抛 IllegalArgumentException。
         * Scenario: blank fromAddress violates the required-field invariant and must throw IllegalArgumentException.
         */
        @Test
        @DisplayName("fromAddress 为空白抛 IllegalArgumentException | Blank fromAddress throws IllegalArgumentException")
        void construct_withBlankFromAddress_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SignRequest(CHAIN_ID, " ", TO_ADDRESS, AMOUNT_WEI_100,
                            null, null, null, null));
        }
    }

    @Nested
    @DisplayName("合法参数构造与字段透传 | Field delegation with valid arguments")
    class FieldDelegation {

        /**
         * 场景：传入全部合法参数（含可选字段），应正确透传所有业务字段。
         * Scenario: All business fields are exposed verbatim when valid args (including optional ones) are provided.
         */
        @Test
        @DisplayName("合法参数下 8 个字段全部透传 | All 8 fields are exposed verbatim with valid args")
        void construct_withValidFields_succeeds() {
            SignRequest request = new SignRequest(CHAIN_ID, FROM_ADDRESS, TO_ADDRESS, AMOUNT_WEI_100,
                    TOKEN_ADDRESS, NONCE_0, GAS_LIMIT_21000, DATA_HEX);

            assertEquals(CHAIN_ID, request.getChainId());
            assertEquals(FROM_ADDRESS, request.getFromAddress());
            assertEquals(TO_ADDRESS, request.getToAddress());
            assertEquals(AMOUNT_WEI_100, request.getAmountWei());
            assertEquals(TOKEN_ADDRESS, request.getTokenAddress());
            assertEquals(NONCE_0, request.getNonce());
            assertEquals(GAS_LIMIT_21000, request.getGasLimit());
            assertEquals(DATA_HEX, request.getData());
        }

        /**
         * 场景：可选字段（tokenAddress/nonce/gasLimit/data）全为 null 时应成功构造。
         * Scenario: Construction succeeds with all optional fields set to null.
         */
        @Test
        @DisplayName("可选字段全 null 成功构造 | Construction with all optional fields null succeeds")
        void construct_withNullOptionalFields_succeeds() {
            SignRequest request = new SignRequest(CHAIN_ID, FROM_ADDRESS, TO_ADDRESS, AMOUNT_WEI_100,
                    null, null, null, null);

            assertEquals(CHAIN_ID, request.getChainId());
            assertNull(request.getTokenAddress());
            assertNull(request.getNonce());
            assertNull(request.getGasLimit());
            assertNull(request.getData());
        }
    }
}
