package io.github.open55.otx.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BizErrorEnum {
    FUND_FLOW_TYPE_NOT_SUPPORT("FUND_FLOW_TYPE_NOT_SUPPORT", "Fund flow type is not supported."),
    FUND_FLOW_TYPE_CANT_NULL("FUND_FLOW_TYPE_CANT_NULL", "Parameters fundFlowType cant be null."),
    UID_INVALID("UID_INVALID", "Parameters uid is invalid."),
    UID_CANT_NULL("UID_CANT_NULL", "Parameters uid cannot be empty."),
    AMOUNT_CANT_NULL("AMOUNT_CANT_NULL", "Parameters amount cannot be empty."),
    PARAM_MISS("PARAM_MISS", "Parameters cannot be empty."),
    CONCURRENCY_ERROR("CONCURRENCY_ERROR", "The current data has been modified by someone else. Please try again."),
    BIZ_NO_EMPTY("BIZ_NO_EMPTY", "The business number cannot be empty."),
    INSUFFICIENT_BALANCE("INSUFFICIENT_BALANCE", "Insufficient account balance."),
    FREEZE_AMOUNT_INVALID("FREEZE_AMOUNT_INVALID", "Freeze amount invalid."),
    ACCOUNT_NOT_EXIST("ACCOUNT_NOT_EXIST", "account not exist."),
    UNFREEZE_AMOUNT_INVALID("UNFREEZE_AMOUNT_INVALID", "unfreeze amount invalid."),
    INSUFFICIENT_FROZEN_BALANCE("INSUFFICIENT_FROZEN_BALANCE", "insufficient frozen balance."),
    WITHDRAW_AMOUNT_INVALID("WITHDRAW_AMOUNT_INVALID", "withdraw amount invalid"),
    LEDGER_AMOUNT_INVALID("LEDGER_AMOUNT_INVALID", "Ledger entry amount must be greater than zero."),
    LEDGER_ACCOUNT_CODE_INVALID("LEDGER_ACCOUNT_CODE_INVALID", "Ledger account code is not in the allowed enum set."),
    LEDGER_ENTRY_TYPE_INVALID("LEDGER_ENTRY_TYPE_INVALID", "Ledger entry type must be DEBIT or CREDIT.");

    private final String code;

    private final String message;
}