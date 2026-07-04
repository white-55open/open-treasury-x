package io.github.open55.otx.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 资金流水持久化对象，映射 fund_flow_t 表。
 */
@EqualsAndHashCode(callSuper = true)
@TableName("fund_flow_t")
@Data
public class FundFlowPO extends BasePO {

    /**
     * 流水号
     */
    private String flowNo;

    /**
     * 用户唯一标识
     */
    private Long uid;

    /**
     * 业务流水号（唯一索引，幂等控制）
     */
    private String bizNo;

    /**
     * 变动金额
     */
    private BigDecimal amount;

    /**
     * 变动前余额
     */
    private BigDecimal balanceBefore;

    /**
     * 变动后余额
     */
    private BigDecimal balanceAfter;

    /**
     * 资金变动方向（IN/OUT）
     */
    private String direction;

    /**
     * 资金流水类型（DEPOSIT/WITHDRAW/FREEZE/UNFREEZE）
     */
    private String type;
}