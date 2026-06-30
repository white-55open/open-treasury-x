package io.github.open55.otx.domain.ledger;

import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.common.entity.BaseEntity;
import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class LedgerEntryEntity extends BaseEntity {

    private final String accountCode;
    private final LedgerEntryTypeEnum entryType;
    private final BigDecimal amount;
    private final Long uid;
    private final String counterparty;
    private final BigDecimal balanceAfter;
    private final String remark;

    public LedgerEntryEntity(
            String accountCode,
            LedgerEntryTypeEnum entryType,
            BigDecimal amount,
            Long uid,
            String counterparty,
            BigDecimal balanceAfter,
            String remark) {
        if (accountCode == null || !isValidAccountCode(accountCode)) {
            throw BizException.get(BizErrorEnum.LEDGER_ACCOUNT_CODE_INVALID);
        }
        if (entryType == null) {
            throw BizException.get(BizErrorEnum.LEDGER_ENTRY_TYPE_INVALID);
        }
        if (amount == null || amount.signum() <= 0) {
            throw BizException.get(BizErrorEnum.LEDGER_AMOUNT_INVALID);
        }
        this.accountCode = accountCode;
        this.entryType = entryType;
        this.amount = amount;
        this.uid = uid;
        this.counterparty = counterparty;
        this.balanceAfter = balanceAfter;
        this.remark = remark;
    }

    private static boolean isValidAccountCode(String accountCode) {
        for (LedgerAccountCodeEnum candidate : LedgerAccountCodeEnum.values()) {
            if (candidate.getCode().equals(accountCode)) {
                return true;
            }
        }
        return false;
    }
}
