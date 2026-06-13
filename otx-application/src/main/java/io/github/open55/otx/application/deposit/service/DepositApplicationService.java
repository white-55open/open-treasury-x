package io.github.open55.otx.application.deposit.service;

import io.github.open55.otx.application.deposit.service.dto.CreateDepositRequest;

public interface DepositApplicationService {
    String deposit(CreateDepositRequest request);
}
