package io.github.open55.otx.application.deposit.service.impl;

import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
import io.github.open55.otx.application.deposit.service.DepositAppService;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositAppServiceImpl implements DepositAppService {

    private final AccountRepo accountRepo;
    private final FundFlowAppService fundFlowAppService;

    @Resource
    @Lazy
    private AccountAppService accountAppService;

    @Resource
    @Lazy
    private DepositAppServiceImpl self;


    @Override
    public String deposit(ChangeAmountRequest request) {
        request.setFundFlowType(FundFlowTypeEnum.DEPOSIT);
        return accountAppService.changeAmountWithFundFlow(request);
    }
}