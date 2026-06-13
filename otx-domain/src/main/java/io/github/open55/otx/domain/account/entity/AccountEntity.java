package io.github.open55.otx.domain.account.entity;

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
        if (getAvailableBalance().compareTo(amount) < 0) {
            throw BizException.get(BizErrorEnum.INSUFFICIENT_BALANCE);
        }
        setAvailableBalance(getAvailableBalance().subtract(amount));
        setFrozenBalance(getFrozenBalance().add(amount));
    }

    public void increaseBalance(BigDecimal amount) {
        setAvailableBalance(getAvailableBalance().add(amount));
    }

    public void deposit(BigDecimal amount) {
        increaseBalance(amount);
    }
}
