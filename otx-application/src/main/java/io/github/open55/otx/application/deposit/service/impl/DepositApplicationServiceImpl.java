package io.github.open55.otx.application.deposit.service.impl;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.application.deposit.service.DepositApplicationService;
import io.github.open55.otx.application.deposit.service.dto.CreateDepositRequest;
import io.github.open55.otx.application.fundflow.dto.request.CreateFundFlowRequest;
import io.github.open55.otx.application.fundflow.service.FundFlowAppService;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.BizIdempotentException;
import io.github.open55.otx.common.exception.OptimisticLockException;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositApplicationServiceImpl implements DepositApplicationService {

    private final AccountRepo accountRepo;
    private final FundFlowAppService fundFlowAppService;
    @Autowired
    @Lazy
    private DepositApplicationServiceImpl self;

    @Override
    public String deposit(CreateDepositRequest request) {
        Assert.notNull(request, () -> BizException.get(BizErrorEnum.MISS_PARAM));
        Assert.notNull(request.getBizNo(), () -> BizException.get(BizErrorEnum.BIZ_NO_EMPTY));
        if (fundFlowAppService.existsBizNo(request.getBizNo())) {
            return request.getBizNo();
        }

        try {
            // record before account update
            self.depositAtomic(request);
        } catch (BizIdempotentException e) {
            //ignored
        }

        return request.getBizNo();
    }

    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5, backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void depositAtomic(CreateDepositRequest request) {
        AccountEntity account = accountRepo.findByUid(request.getUid());
        BigDecimal before = account.getAvailableBalance();
        BigDecimal after = before.add(request.getAmount());

        try {
            // unique key lock
            fundFlowAppService.record(CreateFundFlowRequest.builder()
                    .uid(request.getUid())
                    .bizNo(request.getBizNo())
                    .amount(request.getAmount())
                    .balanceBefore(before)
                    .balanceAfter(after)
                    .direction(FundFlowDirectionEnum.IN)
                    .type(FundFlowTypeEnum.DEPOSIT)
                    .build());
        } catch (DuplicateKeyException ex) {
            throw new BizIdempotentException(ex);
        }

        account.deposit(request.getAmount());
        // Possible OptimisticLockException
        accountRepo.update(account);
    }
}