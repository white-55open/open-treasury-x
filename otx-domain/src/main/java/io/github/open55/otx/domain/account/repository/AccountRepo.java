package io.github.open55.otx.domain.account.repository;

import io.github.open55.otx.domain.account.entity.AccountEntity;

public interface AccountRepo {
    AccountEntity findByUid(Long uid);

    void save(AccountEntity accountEntity);

    void update(AccountEntity accountEntity);
}
