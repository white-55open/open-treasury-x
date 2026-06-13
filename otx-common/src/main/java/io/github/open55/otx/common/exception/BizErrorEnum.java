package io.github.open55.otx.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BizErrorEnum {

    MISS_PARAM("MISS_PARAM", "Parameters cannot be empty."),

    CONCURRENCY_ERROR("CONCURRENCY_ERROR", "The current data has been modified by someone else. Please try again."),

    BIZ_NO_EMPTY("BIZ_NO_EMPTY", "The business number cannot be empty."),

    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", "Insufficient account balance."),

    ACCOUNT_NOT_EXIST("ACCOUNT_NOT_EXIST", "account not exist.");

    private final String code;
    private final String message;
}