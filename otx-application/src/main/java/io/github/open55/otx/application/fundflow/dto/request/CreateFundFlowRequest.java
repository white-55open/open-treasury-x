package io.github.open55.otx.application.fundflow.dto.request;

import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建资金流水请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFundFlowRequest {

    /**
     * 用户唯一标识
     */
    private Long uid;

    /**
     * 业务流水号，用于幂等控制
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
     * 资金流水类型（DEPOSIT/WITHDRAW/FREEZE/UNFREEZE）
     */
    private FundFlowTypeEnum type;
}