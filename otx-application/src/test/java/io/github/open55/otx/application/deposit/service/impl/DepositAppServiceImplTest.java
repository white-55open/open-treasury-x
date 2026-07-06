package io.github.open55.otx.application.deposit.service.impl;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * DepositAppServiceImpl 充值应用服务单元测试。
 * <p>
 * 覆盖充值入口的委托逻辑：设置 DEPOSIT 类型并调用 AccountAppService。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DepositAppServiceImpl 充值应用服务单元测试 | DepositAppServiceImpl unit tests")
class DepositAppServiceImplTest {

    private static final String BIZ_NO = "BIZ-20260101-0001";
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100");

    @Mock
    private AccountAppService accountAppService;

    private DepositAppServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new DepositAppServiceImpl(null, null);
        setField(service, "accountAppService", accountAppService);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * deposit 设置 fundFlowType 为 DEPOSIT 并委托 accountAppService.changeAmountWithFundFlow。
     */
    @Test
    @DisplayName("deposit 委托给 AccountAppService")
    void deposit_delegatesToAccountAppService() {
        ChangeAmountRequest request = new ChangeAmountRequest();
        request.setBizNo(BIZ_NO);
        request.setAmount(AMOUNT_100);
        when(accountAppService.changeAmountWithFundFlow(request)).thenReturn(BIZ_NO);

        String result = service.deposit(request);

        assertEquals(BIZ_NO, result);
        assertEquals(FundFlowTypeEnum.DEPOSIT, request.getFundFlowType());
        verify(accountAppService).changeAmountWithFundFlow(request);
    }
}
