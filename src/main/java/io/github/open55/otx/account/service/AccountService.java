package io.github.open55.otx.account.service;

import io.github.open55.otx.account.entity.Account;

import java.math.BigDecimal;

public interface AccountService {

    Long createAccount(Long uid);

    void increaseBalance(Long uid, BigDecimal amount);

    void freezeBalance(Long uid, BigDecimal amount);

    Account getByUid(Long uid);
}
