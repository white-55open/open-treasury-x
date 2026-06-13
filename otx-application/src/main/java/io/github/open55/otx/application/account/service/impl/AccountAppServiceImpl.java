package io.github.open55.otx.application.account.service.impl;

import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.account.service.AccountAppService;
import io.github.open55.otx.application.assembler.AccountAssembler;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.account.entity.AccountEntity;
import io.github.open55.otx.domain.account.repository.AccountRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountAppServiceImpl implements AccountAppService {

    private final AccountRepo accountRepo;

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
}
