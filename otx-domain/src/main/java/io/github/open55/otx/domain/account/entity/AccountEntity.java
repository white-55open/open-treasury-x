package io.github.open55.otx.domain.account.entity;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.common.entity.BaseEntity;
import lombok.*;

import java.math.BigDecimal;

/**
 * account entity
 */
@Builder
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountEntity extends BaseEntity {
    private Long uid;

    private BigDecimal availableBalance;

    private BigDecimal frozenBalance;

    public static AccountEntity create(Long uid) {
        return new AccountEntityBuilder()
                .uid(uid)
                .build();
    }

    public void freezeBalance(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw BizException.get(BizErrorEnum.FREEZE_AMOUNT_INVALID);
        }
        if (getAvailableBalance().compareTo(amount) < 0) {
            throw BizException.get(BizErrorEnum.INSUFFICIENT_BALANCE);
        }
        setAvailableBalance(getAvailableBalance().subtract(amount));
        setFrozenBalance(getFrozenBalance().add(amount));
    }

    public void withdraw(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw BizException.get(BizErrorEnum.WITHDRAW_AMOUNT_INVALID);
        }
        if (getAvailableBalance().compareTo(amount) < 0) {
            throw BizException.get(BizErrorEnum.INSUFFICIENT_BALANCE);
        }
        Assert.isTrue(getFrozenBalance().compareTo(amount) > 0);
        setFrozenBalance(getFrozenBalance().subtract(amount));
    }

    public void unfreezeBalance(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw BizException.get(BizErrorEnum.UNFREEZE_AMOUNT_INVALID);
        }
        if (getAvailableBalance().compareTo(amount) < 0) {
            throw BizException.get(BizErrorEnum.INSUFFICIENT_FROZEN_BALANCE);
        }
        setFrozenBalance(getFrozenBalance().subtract(amount));
        setAvailableBalance(getAvailableBalance().add(amount));
    }

    public void increaseBalance(BigDecimal amount) {
        setAvailableBalance(getAvailableBalance().add(amount));
    }

    public void deposit(BigDecimal amount) {
        increaseBalance(amount);
    }
}
