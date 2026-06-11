package io.github.open55.otx.account.service.impl;

import io.github.open55.otx.account.dto.response.GetAccountResponse;
import io.github.open55.otx.account.service.AccountAppService;
import io.github.open55.otx.assembler.AccountAssembler;
import io.github.open55.otx.entity.AccountEntity;
import io.github.open55.otx.exception.BizErrorEnum;
import io.github.open55.otx.exception.BizException;
import io.github.open55.otx.repository.AccountRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountAppServiceImpl implements AccountAppService {

    private final AccountRepo accountRepo;

    @Override
    public Long createAccount(Long uid) {
        AccountEntity exist = accountRepo.getByUid(uid);
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
        AccountEntity accountEntity = accountRepo.getByUid(uid);

        if (accountEntity == null) {
            throw BizException.get(BizErrorEnum.ACCOUNT_NOT_EXIST);
        }

        return accountEntity;
    }
}
