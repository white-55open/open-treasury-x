package io.github.open55.otx.domain.fundflow.entity;

import io.github.open55.otx.domain.common.entity.BaseEntity;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 资金流水实体，记录每笔资金变动的明细。
 * <p>
 * 每次账户余额变更（充值、提现、冻结、解冻）都会产生一条资金流水，
 * 用于对账和审计追溯，流水中记录了变动前后的余额快照。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FundFlowEntity extends BaseEntity {
    /**
     * 流水号，由雪花算法唯一生成
     */
    private String flowNo;

    /**
     * 用户唯一标识
     */
    private Long uid;

    /**
     * 业务流水号，用于幂等控制（对应 fund_flow_t.biz_no 唯一索引）
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
     * 资金变动方向（IN-收入，OUT-支出）
     */
    private FundFlowDirectionEnum direction;

    /**
     * 资金流水类型（DEPOSIT-充值，WITHDRAW-提现，FREEZE-冻结，UNFREEZE-解冻）
     */
    private FundFlowTypeEnum type;
}
