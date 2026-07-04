package io.github.open55.otx.domain.account.entity;

import cn.hutool.core.lang.Assert;
import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.domain.common.entity.BaseEntity;
import lombok.*;

import java.math.BigDecimal;

/**
 * 账户实体，对应 account_t 表。
 * <p>
 * 每个用户拥有唯一账户，账户维护可用余额和冻结余额两个状态。
 * 所有资金变动（充值、提现、冻结、解冻）均通过领域方法完成，
 * 确保余额一致性和业务不变量。
 */
@Builder
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountEntity extends BaseEntity {
    /**
     * 用户唯一标识，作为账户的业务键
     */
    private Long uid;

    /**
     * 可用余额，用户可以自由支配的资金
     */
    private BigDecimal availableBalance;

    /**
     * 冻结余额，因提现或业务锁定暂时不可用的资金
     */
    private BigDecimal frozenBalance;

    /**
     * 创建新账户实例。
     *
     * @param uid 用户唯一标识
     * @return 新建的账户实体，初始余额为空（需后续充值或入账设置）
     */
    public static AccountEntity create(Long uid) {
        return new AccountEntityBuilder()
                .uid(uid)
                .build();
    }

    /**
     * 冻结指定金额：将资金从可用余额转移到冻结余额。
     * <p>
     * 业务场景：用户发起提现申请时，先将对应金额冻结，防止重复使用。
     *
     * @param amount 冻结金额，必须大于零
     * @throws BizException 当金额为空或不大于零时抛出 FREEZE_AMOUNT_INVALID；
     *                      当可用余额不足时抛出 INSUFFICIENT_BALANCE
     */
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

    /**
     * 完成提现：从冻结余额中扣减指定金额。
     * <p>
     * 业务场景：提现申请审核通过后，实际扣除已冻结的资金。
     * 调用前需确保对应金额已通过 freezeBalance 冻结。
     *
     * @param amount 提现金额，必须大于零
     * @throws BizException 当金额为空或不大于零时抛出 WITHDRAW_AMOUNT_INVALID；
     *                      当可用余额不足时抛出 INSUFFICIENT_BALANCE；
     *                      当冻结余额不足时抛出 IllegalArgumentException
     */
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

    /**
     * 解冻指定金额：将资金从冻结余额释放回可用余额。
     * <p>
     * 业务场景：提现申请取消或审核驳回时，将已冻结的资金归还可用余额。
     *
     * @param amount 解冻金额，必须大于零
     * @throws BizException 当金额为空或不大于零时抛出 UNFREEZE_AMOUNT_INVALID；
     *                      当冻结余额不足时抛出 INSUFFICIENT_FROZEN_BALANCE
     */
    public void unfreezeBalance(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw BizException.get(BizErrorEnum.UNFREEZE_AMOUNT_INVALID);
        }
        if (getFrozenBalance().compareTo(amount) < 0) {
            throw BizException.get(BizErrorEnum.INSUFFICIENT_FROZEN_BALANCE);
        }
        setFrozenBalance(getFrozenBalance().subtract(amount));
        setAvailableBalance(getAvailableBalance().add(amount));
    }

    /**
     * 增加可用余额（无校验直接加记）。
     * <p>
     * 业务场景：充值到账直接增加可用余额，不做冻结校验。
     *
     * @param amount 增加金额，必须为正数（调用方保证）
     */
    public void increaseBalance(BigDecimal amount) {
        setAvailableBalance(getAvailableBalance().add(amount));
    }

    /**
     * 充值入口，委托给 increaseBalance。
     *
     * @param amount 充值金额
     */
    public void deposit(BigDecimal amount) {
        increaseBalance(amount);
    }
}
