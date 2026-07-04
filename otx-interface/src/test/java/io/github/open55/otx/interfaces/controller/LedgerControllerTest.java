package io.github.open55.otx.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.dto.response.LedgerEntryResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LedgerController 单元测试。
 * <p>
 * 覆盖 POST /ledger/journals 和 GET /ledger/journals/{bizNo} 路由分发与统一响应格式。
 */
@ExtendWith(MockitoExtension.class)
class LedgerControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final LedgerAppService ledgerAppService = Mockito.mock(LedgerAppService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new LedgerController(ledgerAppService)).build();

    /**
     * POST /ledger/journals 正常过账。
     * <p>
     * 给定合法的过账请求体，验证返回 HTTP 200 与 Result<JournalDetailResponseDTO> 格式。
     */
    @Test
    void postJournal_withValidRequest_returnsOk() throws Exception {
        PostJournalRequestDTO req = new PostJournalRequestDTO();
        req.setBizNo("BIZ-001");
        req.setCurrency("USDT");

        JournalDetailResponseDTO resp = new JournalDetailResponseDTO();
        resp.setBizNo("BIZ-001");
        resp.setEntries(List.of(new LedgerEntryResponseDTO()));

        when(ledgerAppService.postJournal(any())).thenReturn(resp);

        mockMvc.perform(post("/ledger/journals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.bizNo").value("BIZ-001"));
    }

    /**
     * GET /ledger/journals/{bizNo} 按 bizNo 查询凭证。
     * <p>
     * 给定存在的 bizNo，验证返回 HTTP 200 与凭证详情响应。
     */
    @Test
    void findByBizNo_withExistingBizNo_returnsOk() throws Exception {
        JournalDetailResponseDTO resp = new JournalDetailResponseDTO();
        resp.setBizNo("BIZ-001");
        resp.setEntries(List.of(new LedgerEntryResponseDTO()));

        when(ledgerAppService.findByBizNo(eq("BIZ-001"))).thenReturn(resp);

        mockMvc.perform(get("/ledger/journals/{bizNo}", "BIZ-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.bizNo").value("BIZ-001"));
    }
}
