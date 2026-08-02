package io.github.open55.otx.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.open55.otx.application.withdraw.dto.request.WithdrawRequestDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawBroadcastResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawSettleResponseDTO;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawStatusResponseDTO;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.response.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WithdrawController 提现控制器单元测试。
 * <p>
 * 覆盖 POST /withdraw/freeze、/settle、/unfreeze 链下账务端点，以及
 * POST /withdraw/broadcast、POST /withdraw/{bizNo}/confirm-settle、
 * POST /withdraw/{bizNo}/cancel、GET /withdraw/{bizNo}/status 链上编排端点的
 * 路由分发、请求体/路径变量映射、Result 包装与业务错误码透传。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawController 提现控制器单元测试 | WithdrawController unit tests")
class WithdrawControllerTest {

    private static final Long TEST_UID = 12345L;
    private static final String BIZ_NO = "WIT-20260101-0001";
    private static final String CURRENCY_USDT = "USDT";
    private static final BigDecimal AMOUNT_50 = new BigDecimal("50");
    private static final String ERR_INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";
    private static final String ERR_INSUFFICIENT_FROZEN_BALANCE = "INSUFFICIENT_FROZEN_BALANCE";
    private static final String ERR_TX_SIGN_FAILED = "TX_SIGN_FAILED";
    private static final String ERR_WITHDRAW_REQUEST_NOT_FOUND = "WITHDRAW_REQUEST_NOT_FOUND";
    private static final String ERR_WITHDRAW_REQUEST_STATUS_INVALID = "WITHDRAW_REQUEST_STATUS_INVALID";
    private static final String TX_HASH = "0x7a9f3c2e5d8b1a4f6c0e3d9b2a5f7c1e4d8a2b6f";
    private static final String STATUS_BROADCASTED = "BROADCASTED";
    private static final String STATUS_SETTLED = "SETTLED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String JOURNAL_POSTED = "POSTED";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WithdrawAppService withdrawAppService = Mockito.mock(WithdrawAppService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new WithdrawController(withdrawAppService))
            .setControllerAdvice(new TestBizExceptionAdvice())
            .build();

    private static WithdrawRequestDTO newRequest() {
        WithdrawRequestDTO req = new WithdrawRequestDTO();
        req.setUid(TEST_UID);
        req.setBizNo(BIZ_NO);
        req.setAmount(AMOUNT_50);
        req.setCurrency(CURRENCY_USDT);
        return req;
    }

    /**
     * 测试用异常处理器：将 BizException 映射为 HTTP 200 与业务错误码，
     * 模拟生产环境 GlobalExceptionHandler 的响应格式。
     */
    @RestControllerAdvice
    static class TestBizExceptionAdvice {

        /**
         * 处理业务异常，返回 HTTP 200 与业务错误码的统一响应。
         *
         * @param ex 业务异常
         * @return 统一失败响应
         */
        @ExceptionHandler(BizException.class)
        @ResponseStatus(HttpStatus.OK)
        Result<Void> handleBizException(BizException ex) {
            return Result.fail(ex.getErrorCode(), ex.getMessage(), null);
        }
    }

    @Nested
    @DisplayName("POST /withdraw/freeze 提现冻结 | withdraw freeze endpoint")
    class Freeze {

        /**
         * 场景：冻结请求成功返回业务流水号。
         * Scenario: freeze request returns bizNo wrapped in Result.
         * 断言 HTTP 200、code=200 且 data 为业务流水号。
         */
        @Test
        @DisplayName("冻结成功返回 bizNo | freeze success returns bizNo")
        void freeze_withValidRequest_returnsBizNo() throws Exception {
            when(withdrawAppService.freeze(any())).thenReturn(BIZ_NO);

            mockMvc.perform(post("/withdraw/freeze")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data").value(BIZ_NO));
        }

        /**
         * 场景：冻结服务抛余额不足异常时错误码透传。
         * Scenario: freeze error code propagates when service throws INSUFFICIENT_BALANCE.
         * 断言 HTTP 200 且 Result.code 为 INSUFFICIENT_BALANCE。
         */
        @Test
        @DisplayName("冻结余额不足错误码透传 | freeze propagates insufficient balance code")
        void freeze_withInsufficientBalance_returnsBizErrorCode() throws Exception {
            when(withdrawAppService.freeze(any()))
                    .thenThrow(BizException.get(BizErrorEnum.INSUFFICIENT_BALANCE));

            mockMvc.perform(post("/withdraw/freeze")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ERR_INSUFFICIENT_BALANCE));
        }
    }

    @Nested
    @DisplayName("POST /withdraw/settle 提现结算 | withdraw settle endpoint")
    class Settle {

        /**
         * 场景：结算请求成功返回业务流水号。
         * Scenario: settle request returns bizNo wrapped in Result.
         * 断言 HTTP 200、code=200 且 data 为业务流水号。
         */
        @Test
        @DisplayName("结算成功返回 bizNo | settle success returns bizNo")
        void settle_withValidRequest_returnsBizNo() throws Exception {
            when(withdrawAppService.withdraw(any())).thenReturn(BIZ_NO);

            mockMvc.perform(post("/withdraw/settle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data").value(BIZ_NO));
        }

        /**
         * 场景：结算服务抛冻结余额不足异常时错误码透传。
         * Scenario: settle error code propagates when service throws INSUFFICIENT_FROZEN_BALANCE.
         * 断言 HTTP 200 且 Result.code 为 INSUFFICIENT_FROZEN_BALANCE。
         */
        @Test
        @DisplayName("结算冻结不足错误码透传 | settle propagates insufficient frozen code")
        void settle_withInsufficientFrozenBalance_returnsBizErrorCode() throws Exception {
            when(withdrawAppService.withdraw(any()))
                    .thenThrow(BizException.get(BizErrorEnum.INSUFFICIENT_FROZEN_BALANCE));

            mockMvc.perform(post("/withdraw/settle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ERR_INSUFFICIENT_FROZEN_BALANCE));
        }
    }

    @Nested
    @DisplayName("POST /withdraw/unfreeze 提现解冻 | withdraw unfreeze endpoint")
    class Unfreeze {

        /**
         * 场景：解冻请求成功返回业务流水号。
         * Scenario: unfreeze request returns bizNo wrapped in Result.
         * 断言 HTTP 200、code=200 且 data 为业务流水号。
         */
        @Test
        @DisplayName("解冻成功返回 bizNo | unfreeze success returns bizNo")
        void unfreeze_withValidRequest_returnsBizNo() throws Exception {
            when(withdrawAppService.unfreeze(any())).thenReturn(BIZ_NO);

            mockMvc.perform(post("/withdraw/unfreeze")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data").value(BIZ_NO));
        }

        /**
         * 场景：解冻服务抛冻结余额不足异常时错误码透传。
         * Scenario: unfreeze error code propagates when service throws INSUFFICIENT_FROZEN_BALANCE.
         * 断言 HTTP 200 且 Result.code 为 INSUFFICIENT_FROZEN_BALANCE。
         */
        @Test
        @DisplayName("解冻冻结不足错误码透传 | unfreeze propagates insufficient frozen code")
        void unfreeze_withInsufficientFrozenBalance_returnsBizErrorCode() throws Exception {
            when(withdrawAppService.unfreeze(any()))
                    .thenThrow(BizException.get(BizErrorEnum.INSUFFICIENT_FROZEN_BALANCE));

            mockMvc.perform(post("/withdraw/unfreeze")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ERR_INSUFFICIENT_FROZEN_BALANCE));
        }
    }

    @Nested
    @DisplayName("POST /withdraw/broadcast 提现广播 | withdraw broadcast endpoint")
    class Broadcast {

        /**
         * 场景：广播请求成功返回广播结果。
         * Scenario: broadcast request returns broadcast result wrapped in Result.
         * 断言 HTTP 200、code=200 且 data 携带 bizNo、txHash、status。
         */
        @Test
        @DisplayName("广播成功返回广播结果 | broadcast success returns broadcast result")
        void broadcast_withValidRequest_returnsBroadcastResult() throws Exception {
            WithdrawBroadcastResponseDTO response = new WithdrawBroadcastResponseDTO();
            response.setBizNo(BIZ_NO);
            response.setTxHash(TX_HASH);
            response.setStatus(STATUS_BROADCASTED);
            when(withdrawAppService.broadcast(any())).thenReturn(response);

            mockMvc.perform(post("/withdraw/broadcast")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data.bizNo").value(BIZ_NO))
                    .andExpect(jsonPath("$.data.txHash").value(TX_HASH))
                    .andExpect(jsonPath("$.data.status").value(STATUS_BROADCASTED));
        }

        /**
         * 场景：广播服务签名失败时错误码透传。
         * Scenario: broadcast error code propagates when service throws TX_SIGN_FAILED.
         * 断言 HTTP 200 且 Result.code 为 TX_SIGN_FAILED。
         */
        @Test
        @DisplayName("广播签名失败错误码透传 | broadcast propagates sign failed code")
        void broadcast_withSignFailed_returnsBizErrorCode() throws Exception {
            when(withdrawAppService.broadcast(any()))
                    .thenThrow(BizException.get(BizErrorEnum.TX_SIGN_FAILED));

            mockMvc.perform(post("/withdraw/broadcast")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(newRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ERR_TX_SIGN_FAILED));
        }
    }

    @Nested
    @DisplayName("POST /withdraw/{bizNo}/confirm-settle 提现确认结算 | withdraw confirm-settle endpoint")
    class ConfirmSettle {

        /**
         * 场景：确认结算请求成功返回结算结果。
         * Scenario: confirm-settle request returns settle result wrapped in Result.
         * 断言 HTTP 200、code=200 且 data 携带 bizNo、status、journalStatus。
         */
        @Test
        @DisplayName("确认结算成功返回结算结果 | confirm-settle success returns settle result")
        void confirmSettle_withExistingBizNo_returnsSettleResult() throws Exception {
            WithdrawSettleResponseDTO response = new WithdrawSettleResponseDTO();
            response.setBizNo(BIZ_NO);
            response.setStatus(STATUS_SETTLED);
            response.setJournalStatus(JOURNAL_POSTED);
            when(withdrawAppService.confirmAndSettle(BIZ_NO)).thenReturn(response);

            mockMvc.perform(post("/withdraw/{bizNo}/confirm-settle", BIZ_NO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data.bizNo").value(BIZ_NO))
                    .andExpect(jsonPath("$.data.status").value(STATUS_SETTLED))
                    .andExpect(jsonPath("$.data.journalStatus").value(JOURNAL_POSTED));
        }

        /**
         * 场景：确认结算时请求不存在错误码透传。
         * Scenario: confirm-settle error code propagates when service throws WITHDRAW_REQUEST_NOT_FOUND.
         * 断言 HTTP 200 且 Result.code 为 WITHDRAW_REQUEST_NOT_FOUND。
         */
        @Test
        @DisplayName("确认结算请求不存在错误码透传 | confirm-settle propagates not found code")
        void confirmSettle_withUnknownBizNo_returnsBizErrorCode() throws Exception {
            when(withdrawAppService.confirmAndSettle(BIZ_NO))
                    .thenThrow(BizException.get(BizErrorEnum.WITHDRAW_REQUEST_NOT_FOUND));

            mockMvc.perform(post("/withdraw/{bizNo}/confirm-settle", BIZ_NO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ERR_WITHDRAW_REQUEST_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("POST /withdraw/{bizNo}/cancel 提现取消 | withdraw cancel endpoint")
    class Cancel {

        /**
         * 场景：取消请求成功返回取消后的请求状态。
         * Scenario: cancel request returns cancelled status wrapped in Result.
         * 断言 HTTP 200、code=200 且 data.status 为 CANCELLED。
         */
        @Test
        @DisplayName("取消成功返回取消状态 | cancel success returns cancelled status")
        void cancel_withExistingBizNo_returnsCancelledStatus() throws Exception {
            WithdrawStatusResponseDTO response = new WithdrawStatusResponseDTO();
            response.setBizNo(BIZ_NO);
            response.setStatus(STATUS_CANCELLED);
            when(withdrawAppService.cancelWithdraw(BIZ_NO)).thenReturn(response);

            mockMvc.perform(post("/withdraw/{bizNo}/cancel", BIZ_NO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data.bizNo").value(BIZ_NO))
                    .andExpect(jsonPath("$.data.status").value(STATUS_CANCELLED));
        }

        /**
         * 场景：取消已结算请求时错误码透传。
         * Scenario: cancel error code propagates when service throws WITHDRAW_REQUEST_STATUS_INVALID.
         * 断言 HTTP 200 且 Result.code 为 WITHDRAW_REQUEST_STATUS_INVALID。
         */
        @Test
        @DisplayName("取消已结算请求错误码透传 | cancel propagates status invalid code")
        void cancel_withSettledBizNo_returnsBizErrorCode() throws Exception {
            when(withdrawAppService.cancelWithdraw(BIZ_NO))
                    .thenThrow(BizException.get(BizErrorEnum.WITHDRAW_REQUEST_STATUS_INVALID));

            mockMvc.perform(post("/withdraw/{bizNo}/cancel", BIZ_NO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ERR_WITHDRAW_REQUEST_STATUS_INVALID));
        }
    }

    @Nested
    @DisplayName("GET /withdraw/{bizNo}/status 提现状态查询 | withdraw status query endpoint")
    class Status {

        /**
         * 场景：状态查询请求成功返回请求当前状态。
         * Scenario: status query returns current status wrapped in Result.
         * 断言 HTTP 200、code=200 且 data 携带 bizNo、status、txHash、amount、currency。
         */
        @Test
        @DisplayName("状态查询成功返回请求状态 | status query success returns current status")
        void status_withExistingBizNo_returnsCurrentStatus() throws Exception {
            WithdrawStatusResponseDTO response = new WithdrawStatusResponseDTO();
            response.setBizNo(BIZ_NO);
            response.setStatus(STATUS_BROADCASTED);
            response.setTxHash(TX_HASH);
            response.setAmount(AMOUNT_50);
            response.setCurrency(CURRENCY_USDT);
            when(withdrawAppService.queryStatus(BIZ_NO)).thenReturn(response);

            mockMvc.perform(get("/withdraw/{bizNo}/status", BIZ_NO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data.bizNo").value(BIZ_NO))
                    .andExpect(jsonPath("$.data.status").value(STATUS_BROADCASTED))
                    .andExpect(jsonPath("$.data.txHash").value(TX_HASH))
                    .andExpect(jsonPath("$.data.amount").value(AMOUNT_50))
                    .andExpect(jsonPath("$.data.currency").value(CURRENCY_USDT));
        }

        /**
         * 场景：状态查询时请求不存在错误码透传。
         * Scenario: status query error code propagates when service throws WITHDRAW_REQUEST_NOT_FOUND.
         * 断言 HTTP 200 且 Result.code 为 WITHDRAW_REQUEST_NOT_FOUND。
         */
        @Test
        @DisplayName("状态查询请求不存在错误码透传 | status query propagates not found code")
        void status_withUnknownBizNo_returnsBizErrorCode() throws Exception {
            when(withdrawAppService.queryStatus(BIZ_NO))
                    .thenThrow(BizException.get(BizErrorEnum.WITHDRAW_REQUEST_NOT_FOUND));

            mockMvc.perform(get("/withdraw/{bizNo}/status", BIZ_NO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ERR_WITHDRAW_REQUEST_NOT_FOUND));
        }
    }
}
