package io.github.open55.otx.application.account.service;

import io.github.open55.otx.application.account.dto.response.GetAccountResponse;
import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;

import java.math.BigDecimal;

public interface AccountAppService {

    Long createAccount(Long uid);

    void increaseBalance(Long uid, BigDecimal amount);

    void freezeBalance(Long uid, BigDecimal amount);

    GetAccountResponse getByUid(Long uid);

    String changeAmountWithFundFlow(ChangeAmountRequest request);
}
