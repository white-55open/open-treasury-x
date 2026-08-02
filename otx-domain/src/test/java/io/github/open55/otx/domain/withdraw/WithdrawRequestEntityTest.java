package io.github.open55.otx.domain.withdraw;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.open55.otx.common.exception.BizException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WithdrawRequestEntity 提现请求聚合根单元测试。
 * <p>
 * 覆盖工厂方法 create() 的构造校验（bizNo/uid/amount 正数/toAddress 等必填字段），
 * 以及状态机全部合法与非法转移（markBroadcasted / markSettled / markFailed / cancel）。
 * <p>
 * Unit tests for the {@link WithdrawRequestEntity} aggregate root.
 * <p>
 * Covers factory create() constructor validations for required fields and all legal and
 * illegal state transitions of the PENDING/BROADCASTED/SETTLED/FAILED/CANCELLED state machine.
 */
@DisplayName("WithdrawRequestEntity 提现请求聚合根单元测试 | WithdrawRequestEntity aggregate root unit tests")
class WithdrawRequestEntityTest {

    private static final long TEST_UID = 12345L;
    private static final String BIZ_NO = "WD-20260802-0001";
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final String CURRENCY_USDT = "USDT";
    private static final String CHAIN_ID = "11155111";
    private static final String TO_ADDRESS = "0x2222222222222222222222222222222222222222";
    private static final String TOKEN_ADDRESS = "0x3333333333333333333333333333333333333333";
    private static final String TX_HASH = "0x9fc76417374aa880d4449a1f7f31ec597f00b1f6f3dd2d258f6e3a5f4c1f5e2a";

    private static final String ERR_BIZ_NO_EMPTY = "BIZ_NO_EMPTY";
    private static final String ERR_UID_CANT_NULL = "UID_CANT_NULL";
    private static final String ERR_WITHDRAW_AMOUNT_INVALID = "WITHDRAW_AMOUNT_INVALID";
    private static final String ERR_PARAM_MISS = "PARAM_MISS";
    private static final String ERR_STATUS_INVALID = "WITHDRAW_REQUEST_STATUS_INVALID";

    /**
     * 构造一条合法的 PENDING 状态提现请求，用于各状态机测试起点。
     */
    private static WithdrawRequestEntity newValidRequest() {
        return WithdrawRequestEntity.create(TEST_UID, BIZ_NO, AMOUNT_100, CURRENCY_USDT,
                CHAIN_ID, TO_ADDRESS, TOKEN_ADDRESS);
    }

    /**
     * 构造一条已广播的提现请求（PENDING → BROADCASTED）。
     */
    private static WithdrawRequestEntity newBroadcastedRequest() {
        WithdrawRequestEntity entity = newValidRequest();
        entity.markBroadcasted(TX_HASH);
        return entity;
    }

    /**
     * 构造一条已失败的提现请求（PENDING → FAILED）。
     */
    private static WithdrawRequestEntity newFailedRequest() {
        WithdrawRequestEntity entity = newValidRequest();
        entity.markFailed();
        return entity;
    }

    /**
     * 构造一条已结算的提现请求（PENDING → BROADCASTED → SETTLED）。
     */
    private static WithdrawRequestEntity newSettledRequest() {
        WithdrawRequestEntity entity = newBroadcastedRequest();
        entity.markSettled();
        return entity;
    }

    @Nested
    @DisplayName("create 工厂方法构造校验 | create factory method constructor validation")
    class ConstructionValidation {

        /**
         * 场景：bizNo 为 null 违反必填约束，应抛 BIZ_NO_EMPTY。
         * Scenario: null bizNo violates the required-field invariant and must throw BIZ_NO_EMPTY.
         */
        @Test
        @DisplayName("bizNo=null 抛 BIZ_NO_EMPTY | Null bizNo throws BIZ_NO_EMPTY")
        void construct_withNullBizNo_throwsBizNoEmpty() {
            BizException ex = assertThrows(BizException.class,
                    () -> WithdrawRequestEntity.create(TEST_UID, null, AMOUNT_100, CURRENCY_USDT,
                            CHAIN_ID, TO_ADDRESS, TOKEN_ADDRESS));

            assertEquals(ERR_BIZ_NO_EMPTY, ex.getErrorCode());
        }

        /**
         * 场景：uid 为 null 违反必填约束，应抛 UID_CANT_NULL。
         * Scenario: null uid violates the required-field invariant and must throw UID_CANT_NULL.
         */
        @Test
        @DisplayName("uid=null 抛 UID_CANT_NULL | Null uid throws UID_CANT_NULL")
        void construct_withNullUid_throwsUidCantNull() {
            BizException ex = assertThrows(BizException.class,
                    () -> WithdrawRequestEntity.create(null, BIZ_NO, AMOUNT_100, CURRENCY_USDT,
                            CHAIN_ID, TO_ADDRESS, TOKEN_ADDRESS));

            assertEquals(ERR_UID_CANT_NULL, ex.getErrorCode());
        }

        /**
         * 场景：amount 为非正数（0 或负数）违反金额不变量，应抛 WITHDRAW_AMOUNT_INVALID。
         * Scenario: non-positive amount (zero or negative) violates the positive-amount invariant
         * and must throw WITHDRAW_AMOUNT_INVALID.
         */
        @Test
        @DisplayName("amount 为非正数抛 WITHDRAW_AMOUNT_INVALID | Non-positive amount throws WITHDRAW_AMOUNT_INVALID")
        void construct_withNonPositiveAmount_throwsAmountInvalid() {
            BizException ex = assertThrows(BizException.class,
                    () -> WithdrawRequestEntity.create(TEST_UID, BIZ_NO, BigDecimal.ZERO, CURRENCY_USDT,
                            CHAIN_ID, TO_ADDRESS, TOKEN_ADDRESS));

            assertEquals(ERR_WITHDRAW_AMOUNT_INVALID, ex.getErrorCode());
        }

        /**
         * 场景：toAddress 为空白违反必填约束（参数缺失），应抛 PARAM_MISS。
         * Scenario: blank toAddress violates the required-address invariant and must throw PARAM_MISS.
         */
        @Test
        @DisplayName("toAddress 空白抛 PARAM_MISS | Blank toAddress throws PARAM_MISS")
        void construct_withBlankToAddress_throwsAddressInvalid() {
            BizException ex = assertThrows(BizException.class,
                    () -> WithdrawRequestEntity.create(TEST_UID, BIZ_NO, AMOUNT_100, CURRENCY_USDT,
                            CHAIN_ID, "   ", TOKEN_ADDRESS));

            assertEquals(ERR_PARAM_MISS, ex.getErrorCode());
        }

        /**
         * 场景：chainId 为空白违反必填约束，应抛 PARAM_MISS。
         * Scenario: blank chainId violates the required-field invariant and must throw PARAM_MISS.
         */
        @Test
        @DisplayName("chainId 空白抛 PARAM_MISS | Blank chainId throws PARAM_MISS")
        void construct_withBlankChainId_throwsParamMiss() {
            BizException ex = assertThrows(BizException.class,
                    () -> WithdrawRequestEntity.create(TEST_UID, BIZ_NO, AMOUNT_100, CURRENCY_USDT,
                            " ", TO_ADDRESS, TOKEN_ADDRESS));

            assertEquals(ERR_PARAM_MISS, ex.getErrorCode());
        }

        /**
         * 场景：传入全部合法参数，应成功构造且状态为 PENDING、可选字段透传。
         * Scenario: Valid args construct successfully with PENDING status and optional fields delegated.
         */
        @Test
        @DisplayName("合法参数构造成功且状态为 PENDING | Valid args construct with PENDING status")
        void construct_withValidFields_succeeds() {
            WithdrawRequestEntity entity = newValidRequest();

            assertEquals(TEST_UID, entity.getUid());
            assertEquals(BIZ_NO, entity.getBizNo());
            assertEquals(AMOUNT_100, entity.getAmount());
            assertEquals(CURRENCY_USDT, entity.getCurrency());
            assertEquals(CHAIN_ID, entity.getChainId());
            assertEquals(TO_ADDRESS, entity.getToAddress());
            assertEquals(TOKEN_ADDRESS, entity.getTokenAddress());
            assertEquals(WithdrawRequestStatusEnum.PENDING, entity.getStatus());
            assertNull(entity.getTxHash());
        }
    }

    @Nested
    @DisplayName("markBroadcasted 标记已广播 | markBroadcasted")
    class MarkBroadcasted {

        /**
         * 场景：PENDING 状态广播成功后状态置为 BROADCASTED 且回填交易哈希。
         * Scenario: markBroadcasted from PENDING sets BROADCASTED status and stores the tx hash.
         */
        @Test
        @DisplayName("PENDING 广播成功置 BROADCASTED 并回填 txHash | Broadcast from PENDING sets BROADCASTED and stores txHash")
        void markBroadcasted_fromPending_setsBroadcastedAndTxHash() {
            WithdrawRequestEntity entity = newValidRequest();

            entity.markBroadcasted(TX_HASH);

            assertEquals(WithdrawRequestStatusEnum.BROADCASTED, entity.getStatus());
            assertEquals(TX_HASH, entity.getTxHash());
        }

        /**
         * 场景：FAILED 状态允许重试广播，成功后置为 BROADCASTED 并更新交易哈希。
         * Scenario: markBroadcasted from FAILED allows retry and sets BROADCASTED with the new tx hash.
         */
        @Test
        @DisplayName("FAILED 重试广播成功置 BROADCASTED | Broadcast retry from FAILED sets BROADCASTED")
        void markBroadcasted_fromFailed_allowsRetry() {
            WithdrawRequestEntity entity = newFailedRequest();

            entity.markBroadcasted(TX_HASH);

            assertEquals(WithdrawRequestStatusEnum.BROADCASTED, entity.getStatus());
            assertEquals(TX_HASH, entity.getTxHash());
        }

        /**
         * 场景：SETTLED 终态不允许再次广播，应抛 WITHDRAW_REQUEST_STATUS_INVALID 且状态不变。
         * Scenario: markBroadcasted from SETTLED throws WITHDRAW_REQUEST_STATUS_INVALID and keeps status.
         */
        @Test
        @DisplayName("SETTLED 再广播抛 WITHDRAW_REQUEST_STATUS_INVALID | Broadcast from SETTLED throws status invalid")
        void markBroadcasted_fromSettled_throwsStatusInvalid() {
            WithdrawRequestEntity entity = newSettledRequest();

            BizException ex = assertThrows(BizException.class, () -> entity.markBroadcasted(TX_HASH));

            assertEquals(ERR_STATUS_INVALID, ex.getErrorCode());
            assertEquals(WithdrawRequestStatusEnum.SETTLED, entity.getStatus());
        }

        /**
         * 场景：txHash 为空白违反非空约束，应抛 IllegalArgumentException 且状态不变。
         * Scenario: blank txHash violates the non-blank invariant and must throw IllegalArgumentException.
         */
        @Test
        @DisplayName("txHash 空白抛 IllegalArgumentException | Blank txHash throws IllegalArgumentException")
        void markBroadcasted_withBlankTxHash_throwsIllegalArgument() {
            WithdrawRequestEntity entity = newValidRequest();

            assertThrows(IllegalArgumentException.class, () -> entity.markBroadcasted(" "));

            assertEquals(WithdrawRequestStatusEnum.PENDING, entity.getStatus());
        }
    }

    @Nested
    @DisplayName("markSettled 标记已结算 | markSettled")
    class MarkSettled {

        /**
         * 场景：BROADCASTED 状态确认达标后结算，状态置为 SETTLED。
         * Scenario: markSettled from BROADCASTED sets SETTLED status.
         */
        @Test
        @DisplayName("BROADCASTED 结算置 SETTLED | Settle from BROADCASTED sets SETTLED")
        void markSettled_fromBroadcasted_setsSettled() {
            WithdrawRequestEntity entity = newBroadcastedRequest();

            entity.markSettled();

            assertEquals(WithdrawRequestStatusEnum.SETTLED, entity.getStatus());
        }

        /**
         * 场景：PENDING 状态（未广播）不允许结算，应抛 WITHDRAW_REQUEST_STATUS_INVALID。
         * Scenario: markSettled from PENDING throws WITHDRAW_REQUEST_STATUS_INVALID.
         */
        @Test
        @DisplayName("PENDING 结算抛 WITHDRAW_REQUEST_STATUS_INVALID | Settle from PENDING throws status invalid")
        void markSettled_fromPending_throwsStatusInvalid() {
            WithdrawRequestEntity entity = newValidRequest();

            BizException ex = assertThrows(BizException.class, entity::markSettled);

            assertEquals(ERR_STATUS_INVALID, ex.getErrorCode());
            assertEquals(WithdrawRequestStatusEnum.PENDING, entity.getStatus());
        }
    }

    @Nested
    @DisplayName("markFailed 标记失败 | markFailed")
    class MarkFailed {

        /**
         * 场景：PENDING 状态签名/广播失败后置为 FAILED（资金保持冻结待处理）。
         * Scenario: markFailed from PENDING sets FAILED status.
         */
        @Test
        @DisplayName("PENDING 失败置 FAILED | Fail from PENDING sets FAILED")
        void markFailed_fromPending_setsFailed() {
            WithdrawRequestEntity entity = newValidRequest();

            entity.markFailed();

            assertEquals(WithdrawRequestStatusEnum.FAILED, entity.getStatus());
        }

        /**
         * 场景：BROADCASTED 状态链上交易失败后置为 FAILED。
         * Scenario: markFailed from BROADCASTED sets FAILED status.
         */
        @Test
        @DisplayName("BROADCASTED 失败置 FAILED | Fail from BROADCASTED sets FAILED")
        void markFailed_fromBroadcasted_setsFailed() {
            WithdrawRequestEntity entity = newBroadcastedRequest();

            entity.markFailed();

            assertEquals(WithdrawRequestStatusEnum.FAILED, entity.getStatus());
        }

        /**
         * 场景：SETTLED 终态不允许再置失败，应抛 WITHDRAW_REQUEST_STATUS_INVALID。
         * Scenario: markFailed from SETTLED throws WITHDRAW_REQUEST_STATUS_INVALID.
         */
        @Test
        @DisplayName("SETTLED 再失败抛 WITHDRAW_REQUEST_STATUS_INVALID | Fail from SETTLED throws status invalid")
        void markFailed_fromSettled_throwsStatusInvalid() {
            WithdrawRequestEntity entity = newSettledRequest();

            BizException ex = assertThrows(BizException.class, entity::markFailed);

            assertEquals(ERR_STATUS_INVALID, ex.getErrorCode());
            assertEquals(WithdrawRequestStatusEnum.SETTLED, entity.getStatus());
        }
    }

    @Nested
    @DisplayName("cancel 取消提现 | cancel")
    class Cancel {

        /**
         * 场景：PENDING 状态取消成功，状态置为 CANCELLED。
         * Scenario: cancel from PENDING sets CANCELLED status.
         */
        @Test
        @DisplayName("PENDING 取消置 CANCELLED | Cancel from PENDING sets CANCELLED")
        void cancel_fromPending_setsCancelled() {
            WithdrawRequestEntity entity = newValidRequest();

            entity.cancel();

            assertEquals(WithdrawRequestStatusEnum.CANCELLED, entity.getStatus());
        }

        /**
         * 场景：BROADCASTED 状态取消成功（应用层先解冻资金再取消），状态置为 CANCELLED。
         * Scenario: cancel from BROADCASTED sets CANCELLED status.
         */
        @Test
        @DisplayName("BROADCASTED 取消置 CANCELLED | Cancel from BROADCASTED sets CANCELLED")
        void cancel_fromBroadcasted_setsCancelled() {
            WithdrawRequestEntity entity = newBroadcastedRequest();

            entity.cancel();

            assertEquals(WithdrawRequestStatusEnum.CANCELLED, entity.getStatus());
        }

        /**
         * 场景：FAILED 状态允许取消，状态置为 CANCELLED。
         * Scenario: cancel from FAILED sets CANCELLED status.
         */
        @Test
        @DisplayName("FAILED 取消置 CANCELLED | Cancel from FAILED sets CANCELLED")
        void cancel_fromFailed_setsCancelled() {
            WithdrawRequestEntity entity = newFailedRequest();

            entity.cancel();

            assertEquals(WithdrawRequestStatusEnum.CANCELLED, entity.getStatus());
        }

        /**
         * 场景：SETTLED 终态不允许取消，应抛 WITHDRAW_REQUEST_STATUS_INVALID 且状态不变。
         * Scenario: cancel from SETTLED throws WITHDRAW_REQUEST_STATUS_INVALID and keeps status.
         */
        @Test
        @DisplayName("SETTLED 取消抛 WITHDRAW_REQUEST_STATUS_INVALID | Cancel from SETTLED throws status invalid")
        void cancel_fromSettled_throwsStatusInvalid() {
            WithdrawRequestEntity entity = newSettledRequest();

            BizException ex = assertThrows(BizException.class, entity::cancel);

            assertEquals(ERR_STATUS_INVALID, ex.getErrorCode());
            assertEquals(WithdrawRequestStatusEnum.SETTLED, entity.getStatus());
        }
    }
}
