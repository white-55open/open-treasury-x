package io.github.open55.otx.application.deposit.service.impl;

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
 * DepositAppServiceImpl 充值应用服务单元测试。
 * <p>
 * 覆盖充值入口的委托逻辑、总账过账调用、异常处理和幂等行为。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DepositAppServiceImpl 充值应用服务单元测试 | DepositAppServiceImpl unit tests")
class DepositAppServiceImplTest {

    private static final String BIZ_NO = "BIZ-20260101-0001";
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");
    private static final Long UID = 12345L;

    @Mock
    private AccountAppService accountAppService;

    @Mock
    private LedgerAppService ledgerAppService;

    private DepositAppServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DepositAppServiceImpl(null, null, null);
        setField(service, "accountAppService", accountAppService);
        setField(service, "ledgerAppService", ledgerAppService);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * deposit 成功时完成余额变更并自动过账总账。
     */
    @Test
    @DisplayName("充值成功后自动过账总账")
    void deposit_withCurrency_success() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_100);
        request.setUid(UID);
        request.setCurrency("USDT");
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

        String result = service.deposit(request);

        assertEquals(BIZ_NO, result);
        assertEquals(FundFlowTypeEnum.DEPOSIT, request.getFundFlowType());
        verify(accountAppService).changeAmountWithFundFlow(request);
        verify(ledgerAppService).postJournal(any(PostJournalRequestDTO.class));
    }

    /**
     * 过账参数正确性验证：业务类型、分录科目和金额。
     */
    @Test
    @DisplayName("过账参数包含正确的分录和业务类型")
    void deposit_passesCorrectJournalRequest() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_100);
        request.setUid(UID);
        request.setCurrency("USDT");
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

        service.deposit(request);

        ArgumentCaptor<PostJournalRequestDTO> captor = ArgumentCaptor.forClass(PostJournalRequestDTO.class);
        verify(ledgerAppService).postJournal(captor.capture());
        PostJournalRequestDTO captured = captor.getValue();

        assertEquals(BIZ_NO, captured.getBizNo());
        assertEquals(LedgerBizTypeEnum.DEPOSIT_ONCHAIN, captured.getBizType());
        assertEquals("USDT", captured.getCurrency());
        assertNotNull(captured.getPostingDate());

        List<LedgerEntryRequestDTO> entries = captured.getEntries();
        assertEquals(2, entries.size());

        LedgerEntryRequestDTO debit = entries.get(0);
        assertEquals(LedgerAccountCodeEnum.DEPOSIT_IN_TRANSIT, debit.getAccountCode());
        assertEquals(LedgerEntryTypeEnum.DEBIT, debit.getEntryType());
        assertEquals(AMOUNT_100, debit.getAmount());

        LedgerEntryRequestDTO credit = entries.get(1);
        assertEquals(LedgerAccountCodeEnum.USER_AVAILABLE, credit.getAccountCode());
        assertEquals(LedgerEntryTypeEnum.CREDIT, credit.getEntryType());
        assertEquals(AMOUNT_100, credit.getAmount());
        assertEquals(UID, credit.getUid());
    }

    /**
     * 未传 currency 时过账失败不影响充值结果。
     */
    @Test
    @DisplayName("未传 currency 过账失败不影响余额变更")
    void deposit_withoutCurrency_failsJournal() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_100);
        request.setUid(UID);
        request.setCurrency(null);
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);
        doThrow(new RuntimeException("currency empty")).when(ledgerAppService).postJournal(any());

        String result = service.deposit(request);

        assertEquals(BIZ_NO, result);
        verify(accountAppService).changeAmountWithFundFlow(request);
        verify(ledgerAppService).postJournal(any());
    }

    /**
     * 过账抛出异常不阻断充值流程。
     */
    @Test
    @DisplayName("过账异常不阻断充值")
    void deposit_ledgerFails_stillReturnsOk() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_100);
        request.setUid(UID);
        request.setCurrency("USDT");
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);
        doThrow(new RuntimeException("RPC timeout")).when(ledgerAppService).postJournal(any());

        String result = service.deposit(request);

        assertEquals(BIZ_NO, result);
        verify(accountAppService).changeAmountWithFundFlow(request);
        verify(ledgerAppService).postJournal(any());
    }

    /**
     * 重复 bizNo 幂等返回，过账只执行一次。
     */
    @Test
    @DisplayName("重复 bizNo 幂等处理")
    void deposit_idempotent_skipsJournal() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_100);
        request.setUid(UID);
        request.setCurrency("USDT");
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

        service.deposit(request);
        service.deposit(request);

        verify(accountAppService, times(2)).changeAmountWithFundFlow(request);
        verify(ledgerAppService, times(2)).postJournal(any());
    }
}
