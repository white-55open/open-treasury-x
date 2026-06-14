package io.github.open55.otx.application.withdraw.service.impl;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.application.withdraw.service.WithdrawAppService;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WithdrawAppServiceImpl implements WithdrawAppService {
    private final AccountRepo accountRepo;

    private final FundFlowAppService fundFlowAppService;

    @Resource
    @Lazy
    private AccountAppService accountAppService;

    @Resource
    @Lazy
    private WithdrawAppServiceImpl self;

    @Override
    public String withdraw(ChangeAmountRequest request) {
        request.setFundFlowType(FundFlowTypeEnum.WITHDRAW);
        return accountAppService.changeAmountWithFundFlow(request);
    }
}