package io.github.open55.otx.account.service;

import io.github.open55.otx.account.dto.response.GetAccountResponse;

import java.math.BigDecimal;

public interface AccountAppService {

    Long createAccount(Long uid);

    void increaseBalance(Long uid, BigDecimal amount);

    void freezeBalance(Long uid, BigDecimal amount);

    GetAccountResponse getByUid(Long uid);
}
