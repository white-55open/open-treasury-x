package io.github.open55.otx.domain.ledger.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LedgerAccountCodeEnum {
    USER_AVAILABLE("USER_AVAILABLE"),
    USER_FROZEN("USER_FROZEN"),
    PLATFORM_HOT("PLATFORM_HOT"),
    PLATFORM_COLD("PLATFORM_COLD"),
    DEPOSIT_IN_TRANSIT("DEPOSIT_IN_TRANSIT"),
    WITHDRAW_IN_TRANSIT("WITHDRAW_IN_TRANSIT"),
    GAS_PAYABLE("GAS_PAYABLE"),
    DEPOSIT_FEE_REVENUE("DEPOSIT_FEE_REVENUE"),
    GAS_EXPENSE("GAS_EXPENSE"),
    WITHDRAW_FEE_EXPENSE("WITHDRAW_FEE_EXPENSE");

    private final String code;
}
