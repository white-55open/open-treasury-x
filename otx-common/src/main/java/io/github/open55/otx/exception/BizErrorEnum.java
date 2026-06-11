package io.github.open55.otx.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BizErrorEnum {

    CONCURRENCY_ERROR("CONCURRENCY_ERROR", "The current data has been modified by someone else. Please try again."),

    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", "Insufficient account balance."),

    ACCOUNT_NOT_EXIST("ACCOUNT_NOT_EXIST", "account not exist.");

    private final String code;
    private final String message;
}