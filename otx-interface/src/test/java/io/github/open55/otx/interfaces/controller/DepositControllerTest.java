package io.github.open55.otx.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.deposit.service.DepositAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DepositController 充值控制器单元测试。
 * <p>
 * 覆盖 POST /deposit 路由分发与统一响应格式。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DepositController 充值控制器单元测试 | DepositController unit tests")
class DepositControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final DepositAppService depositAppService = Mockito.mock(DepositAppService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new DepositController(depositAppService)).build();

    /**
     * POST /deposit 充值成功返回 bizNo。
     */
    @Test
    @DisplayName("POST /deposit 充值成功")
    void deposit_withValidRequest_returnsBizNo() throws Exception {
        ChangeAmountRequest req = new ChangeAmountRequest();
        req.setBizNo("DEP-001");
        req.setAmount(new BigDecimal("100"));

        when(depositAppService.deposit(any())).thenReturn("DEP-001");

        mockMvc.perform(post("/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data").value("DEP-001"));
    }
}
