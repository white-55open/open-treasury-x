package io.github.open55.otx.application.account.service.impl;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.assembler.AccountAssembler;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;
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
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountAppServiceImpl implements AccountAppService {

    private final AccountRepo accountRepo;

    @Lazy
    @Resource
    private FundFlowAppService fundFlowAppService;

    @Lazy
    @Resource
    private AccountAppServiceImpl self;

    @Override
    public Long createAccount(Long uid) {
        AccountEntity exist = accountRepo.findByUid(uid);
        if (exist != null) {
            return exist.getId();
        }

        AccountEntity accountEntity = AccountEntity.create(uid);
        accountRepo.save(accountEntity);
        return accountEntity.getId();
    }

    @Override
    public void increaseBalance(Long uid, BigDecimal amount) {
        AccountEntity accountEntity = getAccountEntityOrThrow(uid);
        accountEntity.increaseBalance(amount);
        accountRepo.update(accountEntity);
    }

    @Override
    public void freezeBalance(Long uid, BigDecimal amount) {
        AccountEntity accountEntity = getAccountEntityOrThrow(uid);
        accountEntity.freezeBalance(amount);
        accountRepo.update(accountEntity);
    }

    @Override
    public GetAccountResponse getByUid(Long uid) {
        AccountEntity entity = getAccountEntityOrThrow(uid);
        return AccountAssembler.INSTANCE.entity2AccountResponse(entity);
    }

    private AccountEntity getAccountEntityOrThrow(Long uid) {
        AccountEntity accountEntity = accountRepo.findByUid(uid);

        if (accountEntity == null) {
            throw BizException.get(BizErrorEnum.ACCOUNT_NOT_EXIST);
        }

        return accountEntity;
    }

    @Override
    public String changeAmountWithFundFlow(ChangeAmountRequest request) {
        Assert.notNull(request, () -> BizException.get(BizErrorEnum.PARAM_MISS));
        Assert.notNull(request.getBizNo(), () -> BizException.get(BizErrorEnum.BIZ_NO_EMPTY));
        Assert.notNull(request.getAmount(), () -> BizException.get(BizErrorEnum.AMOUNT_CANT_NULL));
        Assert.notNull(request.getUid(), () -> BizException.get(BizErrorEnum.UID_CANT_NULL));
        Assert.isTrue(request.getUid() > 0, () -> BizException.get(BizErrorEnum.UID_INVALID));
        Assert.notNull(request.getFundFlowType(), () -> BizException.get(BizErrorEnum.FUND_FLOW_TYPE_CANT_NULL));
        if (fundFlowAppService.existsBizNo(request.getBizNo())) {
            return request.getBizNo();
        }

        try {
            // record before account update
            self.changeAmountWithFundFlowAtomic(request);
        } catch (BizIdempotentException e) {
            //ignored
        }

        return request.getBizNo();
    }

    @Retryable(retryFor = {OptimisticLockException.class}, maxAttempts = 5, backoff = @Backoff(delay = 100, multiplier = 1.5, maxDelay = 250, random = true))
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void changeAmountWithFundFlowAtomic(ChangeAmountRequest request) {
        AccountEntity account = accountRepo.findByUid(request.getUid());
        BigDecimal before = account.getAvailableBalance();
        FundFlowDirectionEnum direction;
        switch (request.getFundFlowType()) {
            case FundFlowTypeEnum.DEPOSIT:
                direction = FundFlowDirectionEnum.IN;
                account.deposit(request.getAmount());
                break;
            case FundFlowTypeEnum.WITHDRAW:
                direction = FundFlowDirectionEnum.OUT;
                account.withdraw(request.getAmount());
                break;
            default:
                throw BizException.get(BizErrorEnum.FUND_FLOW_TYPE_NOT_SUPPORT);
        }
        BigDecimal after = account.getAvailableBalance();

        try {
            // unique key lock
            fundFlowAppService.record(CreateFundFlowRequest.builder()
                    .uid(request.getUid())
                    .bizNo(request.getBizNo())
                    .amount(request.getAmount())
                    .balanceBefore(before)
                    .balanceAfter(after)
                    .direction(direction)
                    .type(request.getFundFlowType())
                    .build());
        } catch (DuplicateKeyException ex) {
            throw new BizIdempotentException(ex);
        }

        // Possible OptimisticLockException
        accountRepo.update(account);
    }
}
