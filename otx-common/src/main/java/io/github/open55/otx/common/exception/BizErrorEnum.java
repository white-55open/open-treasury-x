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
    /**
     * 分录金额必须大于 0，零或负金额的 entry 拒绝构造
     */
    LEDGER_AMOUNT_INVALID("LEDGER_AMOUNT_INVALID", "Ledger entry amount must be greater than zero."),
    /**
     * 会计科目编码不在 LedgerAccountCodeEnum 枚举集合内，非法的系统账户编码
     */
    LEDGER_ACCOUNT_CODE_INVALID("LEDGER_ACCOUNT_CODE_INVALID", "Ledger account code is not in the allowed enum set."),
    /**
     * 分录类型不是 DEBIT 或 CREDIT，不是合法的借贷方向
     */
    LEDGER_ENTRY_TYPE_INVALID("LEDGER_ENTRY_TYPE_INVALID", "Ledger entry type must be DEBIT or CREDIT."),
    /**
     * 分录列表为空，复式记账必须至少包含一条 DEBIT 和一条 CREDIT
     */
    LEDGER_ENTRIES_EMPTY("LEDGER_ENTRIES_EMPTY", "Ledger entries cannot be empty."),
    /**
     * 借贷不平衡，借方金额之和不等于贷方金额之和
     */
    LEDGER_NOT_BALANCED("LEDGER_NOT_BALANCED", "Ledger entries are not balanced."),
    /**
     * 同一账户在同一方向上重复出现，同户同向只能有一条分录
     */
    LEDGER_DUPLICATE_ACCOUNT("LEDGER_DUPLICATE_ACCOUNT", "Duplicate account code with same entry type."),
    /**
     * 业务流水号为空，bizNo 是幂等键不可为空
     */
    LEDGER_BIZ_NO_EMPTY("LEDGER_BIZ_NO_EMPTY", "Ledger bizNo cannot be empty."),
    /**
     * 币种为空，currency 是必填字段
     */
    LEDGER_CURRENCY_EMPTY("LEDGER_CURRENCY_EMPTY", "Ledger currency cannot be empty."),
    /**
     * 按 bizNo 查询不到对应的凭证，Journal 不存在
     */
    LEDGER_JOURNAL_NOT_FOUND("LEDGER_JOURNAL_NOT_FOUND", "Ledger journal not found."),
    /**
     * 凭证状态不是 DRAFT，无法执行过账操作
     */
    LEDGER_JOURNAL_NOT_DRAFT("LEDGER_JOURNAL_NOT_DRAFT", "Ledger journal is not in DRAFT status."),
    /**
     * 凭证状态不是 POSTED，无法执行冲销操作
     */
    LEDGER_JOURNAL_NOT_POSTED("LEDGER_JOURNAL_NOT_POSTED", "Ledger journal is not in POSTED status."),
    /**
     * 反向冲销凭证关联的原凭证找不到，reversal 链路缺失
     */
    LEDGER_REVERSAL_NOT_FOUND("LEDGER_REVERSAL_NOT_FOUND", "Ledger reversal journal not found.");

    private final String code;

    private final String message;
}
