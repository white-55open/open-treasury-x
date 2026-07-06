package io.github.open55.otx.interfaces.controller;

import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.account.service.AccountAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AccountController REST 控制器单元测试。
 * <p>
 * 覆盖账户创建、查询、增额、冻结端点的路由分发与统一响应格式。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountController 账户控制器单元测试 | AccountController unit tests")
class AccountControllerTest {

    private final AccountAppService accountAppService = Mockito.mock(AccountAppService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new AccountController(accountAppService)).build();

    /**
     * POST /accounts/create/{uid} 创建账户成功。
     */
    @Test
    @DisplayName("POST /accounts/create/{uid} 创建成功")
    void create_withValidUid_returnsOk() throws Exception {
        when(accountAppService.createAccount(12345L)).thenReturn(1L);

        mockMvc.perform(post("/accounts/create/{uid}", 12345L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data").value("1"));
    }

    /**
     * GET /accounts/{uid} 查询已存在账户。
     */
    @Test
    @DisplayName("GET /accounts/{uid} 查询成功")
    void get_withExistingUid_returnsAccount() throws Exception {
        GetAccountResponse response = new GetAccountResponse();
        response.setUid(12345L);
        response.setAvailableBalance(new BigDecimal("1000"));
        when(accountAppService.getByUid(12345L)).thenReturn(response);

        mockMvc.perform(get("/accounts/{uid}", 12345L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.uid").value(12345L));
    }

    /**
     * POST /accounts/increase 增加余额成功。
     */
    @Test
    @DisplayName("POST /accounts/increase 增加余额成功")
    void increase_withValidParams_returnsOk() throws Exception {
        mockMvc.perform(post("/accounts/increase")
                        .param("uid", "12345")
                        .param("amount", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }

    /**
     * POST /accounts/freeze 冻结余额成功。
     */
    @Test
    @DisplayName("POST /accounts/freeze 冻结余额成功")
    void freeze_withValidParams_returnsOk() throws Exception {
        mockMvc.perform(post("/accounts/freeze")
                        .param("uid", "12345")
                        .param("amount", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"));
    }
}
