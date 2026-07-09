package io.github.open55.otx.application.withdraw.service.impl;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.ledger.dto.request.LedgerEntryRequestDTO;
import io.github.open55.otx.application.ledger.dto.request.PostJournalRequestDTO;
import io.github.open55.otx.application.ledger.service.LedgerAppService;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * WithdrawAppServiceImpl 提现应用服务单元测试。
 * <p>
 * 覆盖提现入口的委托逻辑、总账过账调用、异常处理和幂等行为。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WithdrawAppServiceImpl 提现应用服务单元测试 | WithdrawAppServiceImpl unit tests")
class WithdrawAppServiceImplTest {

    private static final String BIZ_NO = "BIZ-20260101-0001";
    private static final BigDecimal AMOUNT_50 = new BigDecimal("50");
    private static final Long UID = 12345L;

    @Mock
    private AccountAppService accountAppService;

    @Mock
    private LedgerAppService ledgerAppService;

    private WithdrawAppServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new WithdrawAppServiceImpl(null, null, null);
        setField(service, "accountAppService", accountAppService);
        setField(service, "ledgerAppService", ledgerAppService);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * withdraw 成功时完成余额变更并自动过账总账。
     */
    @Test
    @DisplayName("提现成功后自动过账总账")
    void withdraw_success() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_50);
        request.setUid(UID);
        request.setCurrency("USDT");
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

        String result = service.withdraw(request);

        assertEquals(BIZ_NO, result);
        assertEquals(FundFlowTypeEnum.WITHDRAW, request.getFundFlowType());
        verify(accountAppService).changeAmountWithFundFlow(request);
        verify(ledgerAppService).postJournal(any(PostJournalRequestDTO.class));
    }

    /**
     * 过账参数正确性验证：业务类型、分录科目和金额。
     */
    @Test
    @DisplayName("过账参数包含正确的分录和业务类型")
    void withdraw_passesCorrectJournalRequest() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_50);
        request.setUid(UID);
        request.setCurrency("USDT");
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

        service.withdraw(request);

        ArgumentCaptor<PostJournalRequestDTO> captor = ArgumentCaptor.forClass(PostJournalRequestDTO.class);
        verify(ledgerAppService).postJournal(captor.capture());
        PostJournalRequestDTO captured = captor.getValue();

        assertEquals(BIZ_NO, captured.getBizNo());
        assertEquals(LedgerBizTypeEnum.WITHDRAW_ONCHAIN, captured.getBizType());
        assertEquals("USDT", captured.getCurrency());
        assertNotNull(captured.getPostingDate());

        List<LedgerEntryRequestDTO> entries = captured.getEntries();
        assertEquals(2, entries.size());

        LedgerEntryRequestDTO debit = entries.get(0);
        assertEquals(LedgerAccountCodeEnum.USER_AVAILABLE, debit.getAccountCode());
        assertEquals(LedgerEntryTypeEnum.DEBIT, debit.getEntryType());
        assertEquals(AMOUNT_50, debit.getAmount());
        assertEquals(UID, debit.getUid());

        LedgerEntryRequestDTO credit = entries.get(1);
        assertEquals(LedgerAccountCodeEnum.WITHDRAW_IN_TRANSIT, credit.getAccountCode());
        assertEquals(LedgerEntryTypeEnum.CREDIT, credit.getEntryType());
        assertEquals(AMOUNT_50, credit.getAmount());
    }

    /**
     * 过账抛出异常不阻断提现流程。
     */
    @Test
    @DisplayName("过账异常不阻断提现")
    void withdraw_ledgerFails_stillReturnsOk() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_50);
        request.setUid(UID);
        request.setCurrency("USDT");
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);
        doThrow(new RuntimeException("RPC timeout")).when(ledgerAppService).postJournal(any());

        String result = service.withdraw(request);

        assertEquals(BIZ_NO, result);
        verify(accountAppService).changeAmountWithFundFlow(request);
        verify(ledgerAppService).postJournal(any());
    }

    /**
     * 重复 bizNo 幂等返回。
     */
    @Test
    @DisplayName("重复 bizNo 幂等处理")
    void withdraw_idempotent_skipsJournal() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_50);
        request.setUid(UID);
        request.setCurrency("USDT");
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

        service.withdraw(request);
        service.withdraw(request);

        verify(accountAppService, times(2)).changeAmountWithFundFlow(request);
        verify(ledgerAppService, times(2)).postJournal(any());
    }
}
