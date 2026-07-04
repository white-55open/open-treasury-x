package io.github.open55.otx.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 账户持久化对象，映射 account_t 表。
 */
@EqualsAndHashCode(callSuper = true)
@TableName("account_t")
@Data
public class AccountPO extends BasePO {

    /**
     * 用户唯一标识
     */
    private Long uid;

    /**
     * 可用余额
     */
    private BigDecimal availableBalance;

    /**
     * 冻结余额
     */
    private BigDecimal frozenBalance;
}