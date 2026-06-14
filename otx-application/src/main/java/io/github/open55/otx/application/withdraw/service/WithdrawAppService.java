package io.github.open55.otx.application.withdraw.service;

import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;

public interface WithdrawAppService {

    String withdraw(ChangeAmountRequest request);
}