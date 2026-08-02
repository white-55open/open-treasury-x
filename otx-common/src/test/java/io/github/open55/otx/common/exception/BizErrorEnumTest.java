package io.github.open55.otx.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务错误码枚举单元测试，覆盖错误码与消息的非空性与全局唯一性。
 * <p>
 * 防止新增错误码时误用重复 code 或遗漏 message，保证业务码作为
 * 上游调用方判定依据的稳定契约。
 */
@DisplayName("BizErrorEnum 业务错误码枚举单元测试 | BizErrorEnum unit tests")
class BizErrorEnumTest {

    /**
     * 场景：遍历全部错误码，断言 code 与 message 均非空且 code 全局唯一。
     * Scenario: every error code has non-blank code and message, and all codes are unique.
     */
    @Test
    @DisplayName("全部错误码 code/message 非空且 code 唯一 | all codes are non-blank and unique")
    void allCodes_areUniqueAndNonBlank() {
        BizErrorEnum[] values = BizErrorEnum.values();

        assertTrue(values.length > 0, "错误码枚举不应为空");

        Set<String> codeSet = new HashSet<>();
        for (BizErrorEnum errorEnum : values) {
            assertNotNull(errorEnum.getCode(), "错误码 code 不能为空");
            assertFalse(errorEnum.getCode().isBlank(), "错误码 code 不能为空白");
            assertNotNull(errorEnum.getMessage(), "错误码 message 不能为空");
            assertFalse(errorEnum.getMessage().isBlank(), "错误码 message 不能为空白");
            assertTrue(codeSet.add(errorEnum.getCode()), "错误码 code 必须全局唯一，重复码: " + errorEnum.getCode());
        }
    }

    /**
     * 场景：链上交易类与提现请求类新增错误码存在且 code 与字面量一致。
     * Scenario: chain-transaction and withdraw-request error codes exist with consistent literal.
     */
    @Test
    @DisplayName("链上交易与提现请求新增错误码就位 | new chain-tx and withdraw error codes are present")
    void newTxAndWithdrawErrorCodes_arePresent() {
        assertEquals("TX_SIGN_FAILED", BizErrorEnum.TX_SIGN_FAILED.getCode());
        assertEquals("TX_BROADCAST_FAILED", BizErrorEnum.TX_BROADCAST_FAILED.getCode());
        assertEquals("TX_NOT_CONFIRMED_YET", BizErrorEnum.TX_NOT_CONFIRMED_YET.getCode());
        assertEquals("TX_CHAIN_FAILED", BizErrorEnum.TX_CHAIN_FAILED.getCode());
        assertEquals("WITHDRAW_REQUEST_NOT_FOUND", BizErrorEnum.WITHDRAW_REQUEST_NOT_FOUND.getCode());
        assertEquals("WITHDRAW_REQUEST_STATUS_INVALID", BizErrorEnum.WITHDRAW_REQUEST_STATUS_INVALID.getCode());
    }

    /**
     * 场景：充值链上确认类错误码存在且 code 与字面量一致。
     * Scenario: deposit chain-confirmation error codes exist with consistent literal.
     */
    @Test
    @DisplayName("充值链上确认错误码就位 | new deposit chain-confirmation error codes are present")
    void errorEnum_declaresDepositChainErrorCodes_withUniqueCode() {
        assertEquals("DEPOSIT_CHAIN_INFO_MISS", BizErrorEnum.DEPOSIT_CHAIN_INFO_MISS.getCode());
        assertEquals("DEPOSIT_TX_NOT_CONFIRMED", BizErrorEnum.DEPOSIT_TX_NOT_CONFIRMED.getCode());
        assertEquals("DEPOSIT_CHAIN_QUERY_FAILED", BizErrorEnum.DEPOSIT_CHAIN_QUERY_FAILED.getCode());

        // 全局唯一性：新增 3 码后 code 仍无重复
        Set<String> codeSet = new HashSet<>();
        for (BizErrorEnum errorEnum : BizErrorEnum.values()) {
            assertTrue(codeSet.add(errorEnum.getCode()), "错误码 code 必须全局唯一，重复码: " + errorEnum.getCode());
        }
    }
}
