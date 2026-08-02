package io.github.open55.otx.application.deposit.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DepositRequestDTO 充值入账请求 DTO 单元测试。
 * <p>
 * 覆盖继承自 ChangeAmountRequest 的通用字段透传与新增链上证据字段的读写行为。
 * English: Unit tests for the deposit request DTO, covering field inheritance
 * from ChangeAmountRequest and the new chain-evidence fields.
 */
@DisplayName("DepositRequestDTO 充值入账请求 DTO 单元测试 | DepositRequestDTO unit tests")
class DepositRequestDTOTest {

    /** 测试用户唯一标识 */
    private static final Long TEST_UID = 1001L;

    /** 测试充值金额 */
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");

    /** 测试业务流水号 */
    private static final String TEST_BIZ_NO = "DEP-001";

    /** 测试币种 */
    private static final String TEST_CURRENCY = "USDT";

    /** 测试区块链 ID */
    private static final String TEST_CHAIN_ID = "11155111";

    /** 测试链上交易哈希 */
    private static final String TEST_TX_HASH = "0xabc123def456";

    /** 测试请求级确认数 */
    private static final int REQ_CONFIRMATIONS_6 = 6;

    /** 测试代币合约地址 */
    private static final String TEST_TOKEN_ADDRESS = "0xTokenContract";

    /**
     * 场景：构造带全部字段的充值请求 DTO，验证继承字段透传正常且链字段可读写。
     * Scenario: a fully populated deposit request DTO exposes inherited fields
     * and round-trips its chain-evidence fields.
     */
    @Test
    @DisplayName("继承字段透传且链字段可读写 | inherited fields pass through and chain fields round-trip")
    void depositRequestDTO_inheritsChangeAmountFields_andCarriesChainFields() {
        // Arrange：构造带继承字段与链字段的完整请求
        DepositRequestDTO request = new DepositRequestDTO();
        request.setUid(TEST_UID);
        request.setAmount(AMOUNT_100);
        request.setBizNo(TEST_BIZ_NO);
        request.setCurrency(TEST_CURRENCY);
        request.setChainId(TEST_CHAIN_ID);
        request.setChainTxHash(TEST_TX_HASH);
        request.setRequiredConfirmations(REQ_CONFIRMATIONS_6);
        request.setTokenAddress(TEST_TOKEN_ADDRESS);

        // Act & Assert：继承字段透传读取
        assertEquals(TEST_UID, request.getUid());
        assertEquals(AMOUNT_100, request.getAmount());
        assertEquals(TEST_BIZ_NO, request.getBizNo());
        assertEquals(TEST_CURRENCY, request.getCurrency());

        // Assert：链字段读写
        assertEquals(TEST_CHAIN_ID, request.getChainId());
        assertEquals(TEST_TX_HASH, request.getChainTxHash());
        assertEquals(REQ_CONFIRMATIONS_6, request.getRequiredConfirmations());
        assertEquals(TEST_TOKEN_ADDRESS, request.getTokenAddress());
    }
}
