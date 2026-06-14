package io.github.open55.otx.application.deposit.service;

import io.github.open55.otx.application.deposit.dto.ChangeAmountRequest;

public interface DepositAppService {
    String deposit(ChangeAmountRequest request);
}
