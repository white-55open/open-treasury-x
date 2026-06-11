package io.github.open55.otx.entity;

import io.github.open55.otx.exception.BizErrorEnum;
import io.github.open55.otx.exception.BizException;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * account entity
 */
@Builder
@EqualsAndHashCode(callSuper = true)
@Data
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
        if (getAvailableBalance().compareTo(amount) < 0) {
            throw BizException.get(BizErrorEnum.INSUFFICIENT_BALANCE);
        }
        setAvailableBalance(getAvailableBalance().subtract(amount));
        setFrozenBalance(getFrozenBalance().add(amount));
    }

    public void increaseBalance(BigDecimal amount) {
        setAvailableBalance(getAvailableBalance().add(amount));
    }
}
