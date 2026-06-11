package io.github.open55.otx.repository;

import io.github.open55.otx.entity.AccountEntity;

public interface AccountRepo {
    AccountEntity getByUid(Long uid);

    void save(AccountEntity accountEntity);

    void update(AccountEntity accountEntity);
}
