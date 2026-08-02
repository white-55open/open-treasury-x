package io.github.open55.otx.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open55.otx.application.deposit.dto.DepositRequestDTO;
import io.github.open55.otx.application.deposit.service.DepositAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DepositController 充值控制器单元测试。
 * <p>
 * 覆盖 POST /deposit 路由分发、统一响应格式与含链上证据的 JSON 请求体绑定。
 * English: Unit tests for the deposit REST controller, covering routing,
 * the unified response envelope and binding of chain-evidence JSON fields.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DepositController 充值控制器单元测试 | DepositController unit tests")
class DepositControllerTest {

    private static final String TEST_BIZ_NO = "DEP-001";
    private static final BigDecimal TEST_AMOUNT_100 = new BigDecimal("100");
    private static final Long TEST_UID = 1001L;
    private static final String TEST_CHAIN_ID = "11155111";
    private static final String TEST_TX_HASH = "0xabc123def456";
    private static final int REQ_CONFIRMATIONS_12 = 12;
    private static final String TEST_TOKEN_ADDRESS = "0xTokenContract";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DepositAppService depositAppService = Mockito.mock(DepositAppService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new DepositController(depositAppService)).build();

    /**
     * 场景：携带链上证据的 JSON 请求体调用 POST /deposit，返回统一响应与业务流水号。
     * Scenario: POST /deposit with a chain-evidence JSON body returns the bizNo in the unified envelope.
     */
    @Test
    @DisplayName("POST /deposit 充值成功返回 bizNo | POST /deposit returns the bizNo")
    void deposit_withValidRequest_returnsBizNo() throws Exception {
        // Arrange：构造带链上证据的充值请求体
        DepositRequestDTO req = new DepositRequestDTO();
        req.setBizNo(TEST_BIZ_NO);
        req.setAmount(TEST_AMOUNT_100);
        req.setUid(TEST_UID);
        req.setChainId(TEST_CHAIN_ID);
        req.setChainTxHash(TEST_TX_HASH);
        when(depositAppService.deposit(any())).thenReturn(TEST_BIZ_NO);

        // Act & Assert：发起充值请求，断言业务码与返回的业务流水号
        mockMvc.perform(post("/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data").value(TEST_BIZ_NO));
    }

    /**
     * 场景：JSON 请求体的链字段绑定到 DepositRequestDTO 并原样透传给应用服务。
     * Scenario: chain-evidence JSON fields bind onto DepositRequestDTO and pass through to the app service.
     */
    @Test
    @DisplayName("链上证据 JSON 绑定到充值请求 DTO | chain-evidence JSON binds to the deposit DTO")
    void deposit_withChainEvidenceJson_bindsToDepositRequestDTO() throws Exception {
        // Arrange：构造含链字段（含可选覆盖值与代币地址）的 JSON 请求体
        String jsonBody = """
                {
                  "uid": 1001,
                  "amount": 100,
                  "bizNo": "DEP-001",
                  "currency": "USDT",
                  "chainId": "11155111",
                  "chainTxHash": "0xabc123def456",
                  "requiredConfirmations": 12,
                  "tokenAddress": "0xTokenContract"
                }
                """;
        when(depositAppService.deposit(any(DepositRequestDTO.class))).thenReturn(TEST_BIZ_NO);

        // Act：发起充值请求
        mockMvc.perform(post("/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk());

        // Assert：捕获应用服务入参，断言链字段完整绑定
        ArgumentCaptor<DepositRequestDTO> captor = ArgumentCaptor.forClass(DepositRequestDTO.class);
        verify(depositAppService).deposit(captor.capture());
        DepositRequestDTO captured = captor.getValue();
        assertEquals(TEST_CHAIN_ID, captured.getChainId());
        assertEquals(TEST_TX_HASH, captured.getChainTxHash());
        assertEquals(REQ_CONFIRMATIONS_12, captured.getRequiredConfirmations());
        assertEquals(TEST_TOKEN_ADDRESS, captured.getTokenAddress());
    }
}
