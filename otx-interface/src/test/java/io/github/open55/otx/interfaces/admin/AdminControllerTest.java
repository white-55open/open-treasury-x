package io.github.open55.otx.interfaces.admin;

import io.github.open55.otx.application.account.dto.response.AccountSummaryDTO;
import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.application.ledger.dto.response.JournalDetailResponseDTO;
import io.github.open55.otx.application.ledger.dto.response.JournalSummaryDTO;
import io.github.open55.otx.application.ledger.dto.response.LedgerEntryResponseDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.application.withdraw.dto.response.WithdrawRequestViewDTO;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.response.Result;
import io.github.open55.otx.domain.fundflow.entity.FundFlowEntity;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 管理控制台页面控制器单元测试。
 * <p>
 * 覆盖 /admin 导航、账户总览、提现单据列表与结算/取消操作、用户流水时间线、
 * 凭证列表与分录详情的路由分发、模型装配、重定向与 BizException 错误展示路径。
 * 页面控制器返回视图名而非 Result，断言视图名与模型属性。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("管理控制台页面控制器单元测试 | Admin console controllers unit tests")
class AdminControllerTest {

    private static final Long TEST_UID = 12345L;
    private static final String BIZ_NO = "WIT-20260101-0001";
    private static final BigDecimal AMOUNT_50 = new BigDecimal("50");
    private static final String STATUS_BROADCASTED = "BROADCASTED";
    private static final String ERROR_MESSAGE = "WITHDRAW_REQUEST_STATUS_INVALID: 提现请求状态非法";
    private static final String VIEW_INDEX = "admin/index";
    private static final String VIEW_ACCOUNTS = "admin/accounts";
    private static final String VIEW_WITHDRAWS = "admin/withdraws";
    private static final String VIEW_FLOWS = "admin/flows";
    private static final String VIEW_JOURNALS = "admin/journals";
    private static final String VIEW_JOURNAL_DETAIL = "admin/journal-detail";

    private final AccountAppService accountAppService = Mockito.mock(AccountAppService.class);

    private final WithdrawAppService withdrawAppService = Mockito.mock(WithdrawAppService.class);

    private final FundFlowAppService fundFlowAppService = Mockito.mock(FundFlowAppService.class);

    private final LedgerAppService ledgerAppService = Mockito.mock(LedgerAppService.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new AdminHomeController(),
                    new AdminAccountController(accountAppService, fundFlowAppService),
                    new AdminWithdrawController(withdrawAppService),
                    new AdminJournalController(ledgerAppService))
            .setControllerAdvice(new TestBizExceptionAdvice())
            .build();

    /**
     * 测试用异常处理器：模拟生产环境 GlobalExceptionHandler 的响应格式，
     * 页面控制器已捕获 BizException，此 advice 兜底非预期异常路径。
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

    private static AccountSummaryDTO newAccountSummary() {
        AccountSummaryDTO dto = new AccountSummaryDTO();
        dto.setUid(TEST_UID);
        dto.setAvailableBalance(AMOUNT_50);
        dto.setFrozenBalance(BigDecimal.ZERO);
        return dto;
    }

    private static WithdrawRequestViewDTO newWithdrawView() {
        WithdrawRequestViewDTO dto = new WithdrawRequestViewDTO();
        dto.setBizNo(BIZ_NO);
        dto.setUid(TEST_UID);
        dto.setAmount(AMOUNT_50);
        dto.setCurrency("USDT");
        dto.setChainId("11155111");
        dto.setToAddress("0xToAddress00000000000000000000000000000001");
        dto.setTxHash("0xabc123");
        dto.setStatus(STATUS_BROADCASTED);
        return dto;
    }

    private static FundFlowEntity newFundFlow() {
        FundFlowEntity flow = new FundFlowEntity();
        flow.setFlowNo("FLOW-0001");
        flow.setUid(TEST_UID);
        flow.setBizNo(BIZ_NO);
        flow.setAmount(AMOUNT_50);
        flow.setBalanceBefore(BigDecimal.ZERO);
        flow.setBalanceAfter(AMOUNT_50);
        flow.setDirection(FundFlowDirectionEnum.IN);
        flow.setType(FundFlowTypeEnum.DEPOSIT);
        return flow;
    }

    private static JournalSummaryDTO newJournalSummary() {
        JournalSummaryDTO dto = new JournalSummaryDTO();
        dto.setBizNo(BIZ_NO);
        dto.setBizType("DEPOSIT_ONCHAIN");
        dto.setStatus("POSTED");
        dto.setTotalAmount(AMOUNT_50);
        dto.setPostingDate(LocalDate.of(2026, 1, 1));
        dto.setChainTxHash("0xabc123");
        return dto;
    }

    private static JournalDetailResponseDTO newJournalDetail() {
        JournalDetailResponseDTO dto = new JournalDetailResponseDTO();
        dto.setBizNo(BIZ_NO);
        dto.setBizType("DEPOSIT_ONCHAIN");
        dto.setStatus("POSTED");
        dto.setTotalAmount(new BigDecimal("100"));
        dto.setPostingDate(LocalDate.of(2026, 1, 1));
        LedgerEntryResponseDTO debit = new LedgerEntryResponseDTO();
        debit.setAccountCode("PLATFORM_HOT");
        debit.setEntryType("DEBIT");
        debit.setAmount(new BigDecimal("100"));
        LedgerEntryResponseDTO credit = new LedgerEntryResponseDTO();
        credit.setAccountCode("DEPOSIT_IN_TRANSIT");
        credit.setEntryType("CREDIT");
        credit.setAmount(new BigDecimal("100"));
        dto.setEntries(List.of(debit, credit));
        return dto;
    }

    @Nested
    @DisplayName("GET /admin 控制台导航 | admin home navigation")
    class Home {

        /**
         * 场景：访问控制台根路径返回导航页视图。
         * Scenario: the navigation view is rendered for the console root path.
         * 断言视图名为 admin/index 且无模型错误。
         */
        @Test
        @DisplayName("返回导航页视图 | returns the navigation view")
        void index_returnsNavigationView() throws Exception {
            mockMvc.perform(get("/admin"))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VIEW_INDEX));
        }

        /**
         * 场景：导航页流水入口提交 uid 后重定向到用户流水页。
         * Scenario: the flows entry with a uid redirects to the user flows page.
         * 断言响应为重定向到 /admin/accounts/{uid}/flows。
         */
        @Test
        @DisplayName("流水入口按 uid 重定向到用户流水页 | flows entry redirects to the user flows page")
        void flowsRedirect_redirectsToUserFlowsPage() throws Exception {
            mockMvc.perform(get("/admin/flows").param("uid", String.valueOf(TEST_UID)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/accounts/" + TEST_UID + "/flows"));
        }
    }

    @Nested
    @DisplayName("GET /admin/accounts 账户总览 | account overview page")
    class Accounts {

        /**
         * 场景：账户总览页渲染账户列表。
         * Scenario: the accounts page renders the account summaries.
         * 断言视图名与模型属性 accounts。
         */
        @Test
        @DisplayName("返回账户总览页与账户列表 | returns the accounts page with account list")
        void accountsPage_returnsList() throws Exception {
            when(accountAppService.listAccounts()).thenReturn(List.of(newAccountSummary()));

            mockMvc.perform(get("/admin/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VIEW_ACCOUNTS))
                    .andExpect(model().attributeExists("accounts"));
        }
    }

    @Nested
    @DisplayName("GET /admin/accounts/{uid}/flows 用户流水时间线 | user flows page")
    class Flows {

        /**
         * 场景：按用户标识渲染该用户的流水时间线。
         * Scenario: the flows page renders the fund flows for the given uid.
         * 断言视图名、uid 模型属性与流水列表，且查询按 uid 转发到应用服务。
         */
        @Test
        @DisplayName("返回用户流水页与流水列表 | returns the flows page with fund flows")
        void flowsPage_returnsFlowsForUid() throws Exception {
            when(fundFlowAppService.findByUid(TEST_UID)).thenReturn(List.of(newFundFlow()));

            mockMvc.perform(get("/admin/accounts/{uid}/flows", TEST_UID))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VIEW_FLOWS))
                    .andExpect(model().attribute("uid", TEST_UID))
                    .andExpect(model().attributeExists("flows"));

            verify(fundFlowAppService).findByUid(TEST_UID);
        }
    }

    @Nested
    @DisplayName("GET /admin/withdraws 提现单据列表 | withdraw requests page")
    class Withdraws {

        /**
         * 场景：提现单据页渲染提现请求列表。
         * Scenario: the withdraws page renders the withdraw request views.
         * 断言视图名与模型属性 withdraws。
         */
        @Test
        @DisplayName("返回提现单据页与单据列表 | returns the withdraws page with request list")
        void withdrawsPage_returnsList() throws Exception {
            when(withdrawAppService.listRequests()).thenReturn(List.of(newWithdrawView()));

            mockMvc.perform(get("/admin/withdraws"))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VIEW_WITHDRAWS))
                    .andExpect(model().attributeExists("withdraws"));
        }

        /**
         * 场景：确认结算成功时调用应用服务并重定向回单据列表。
         * Scenario: confirm-settle invokes the use case and redirects back to the list.
         * 断言 confirmAndSettle 被调用且响应为重定向。
         */
        @Test
        @DisplayName("确认结算提交用例并重定向 | confirm-settle submits the use case and redirects")
        void confirmSettle_submitsUseCase() throws Exception {
            mockMvc.perform(post("/admin/withdraws/{bizNo}/confirm-settle", BIZ_NO))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/withdraws"));

            verify(withdrawAppService).confirmAndSettle(BIZ_NO);
        }

        /**
         * 场景：确认结算抛出 BizException 时渲染列表页并展示错误消息。
         * Scenario: confirm-settle renders the error block when the use case throws BizException.
         * 断言 HTTP 200、视图为列表页且模型含错误消息与最新列表。
         */
        @Test
        @DisplayName("确认结算业务异常渲染错误块 | confirm-settle renders the error message on BizException")
        void confirmSettle_whenBizException_rendersError() throws Exception {
            when(withdrawAppService.confirmAndSettle(BIZ_NO))
                    .thenThrow(BizException.get(BizErrorEnum.WITHDRAW_REQUEST_STATUS_INVALID));
            when(withdrawAppService.listRequests()).thenReturn(List.of(newWithdrawView()));

            mockMvc.perform(post("/admin/withdraws/{bizNo}/confirm-settle", BIZ_NO))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VIEW_WITHDRAWS))
                    .andExpect(model().attributeExists("errorMessage"))
                    .andExpect(model().attributeExists("withdraws"));
        }

        /**
         * 场景：取消提现成功时调用应用服务并重定向回单据列表。
         * Scenario: cancel invokes the use case and redirects back to the list.
         * 断言 cancelWithdraw 被调用且响应为重定向。
         */
        @Test
        @DisplayName("取消提现提交用例并重定向 | cancel submits the use case and redirects")
        void cancel_submitsUseCase() throws Exception {
            mockMvc.perform(post("/admin/withdraws/{bizNo}/cancel", BIZ_NO))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/withdraws"));

            verify(withdrawAppService).cancelWithdraw(BIZ_NO);
        }
    }

    @Nested
    @DisplayName("GET /admin/journals 凭证列表与详情 | journals list and detail pages")
    class Journals {

        /**
         * 场景：凭证列表页渲染凭证摘要列表。
         * Scenario: the journals page renders the journal summaries.
         * 断言视图名与模型属性 journals。
         */
        @Test
        @DisplayName("返回凭证列表页与凭证摘要 | returns the journals page with journal summaries")
        void journalsPage_returnsList() throws Exception {
            when(ledgerAppService.listJournals()).thenReturn(List.of(newJournalSummary()));

            mockMvc.perform(get("/admin/journals"))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VIEW_JOURNALS))
                    .andExpect(model().attributeExists("journals"));
        }

        /**
         * 场景：凭证详情页渲染单张凭证及全部分录。
         * Scenario: the journal detail page renders the journal with its entries.
         * 断言视图名与模型属性 journal，且详情按 bizNo 转发到应用服务。
         */
        @Test
        @DisplayName("返回凭证详情页与分录列表 | returns the detail page with entries")
        void journalDetailPage_returnsEntries() throws Exception {
            when(ledgerAppService.findByBizNo(BIZ_NO)).thenReturn(newJournalDetail());

            mockMvc.perform(get("/admin/journals/{bizNo}", BIZ_NO))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VIEW_JOURNAL_DETAIL))
                    .andExpect(model().attributeExists("journal"));

            verify(ledgerAppService).findByBizNo(BIZ_NO);
        }
    }
}
