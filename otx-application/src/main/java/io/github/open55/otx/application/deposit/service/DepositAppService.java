package io.github.open55.otx.application.deposit.service;

import io.github.open55.otx.application.deposit.dto.DepositRequestDTO;

public interface DepositAppService {
    String deposit(DepositRequestDTO request);
}
