package io.github.open55.otx.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
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
 * WithdrawController 提现控制器单元测试。
 * <p>
 * 覆盖 POST /withdraw 路由分发与统一响应格式。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawController 提现控制器单元测试 | WithdrawController unit tests")
class WithdrawControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WithdrawAppService withdrawAppService = Mockito.mock(WithdrawAppService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new WithdrawController(withdrawAppService)).build();

    /**
     * POST /withdraw 提现成功返回 bizNo。
     */
    @Test
    @DisplayName("POST /withdraw 提现成功")
    void withdraw_withValidRequest_returnsBizNo() throws Exception {
        ChangeAmountRequest req = new ChangeAmountRequest();
        req.setBizNo("WIT-001");
        req.setAmount(new BigDecimal("100"));

        when(withdrawAppService.withdraw(any())).thenReturn("WIT-001");

        mockMvc.perform(post("/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data").value("WIT-001"));
    }
}
