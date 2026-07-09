package io.github.open55.otx.application.deposit.dto;

import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 资金变更请求，充值（Deposit）和提现（Withdraw）共用此 DTO。
 */
@Data
public class ChangeAmountRequest {

    /**
     * 用户唯一标识
     */
    private Long uid;

    /**
     * 变更金额
     */
    private BigDecimal amount;

    /**
     * 业务流水号，用于幂等控制
     */
    private String bizNo;

    /**
     * 资金流水类型（DEPOSIT 或 WITHDRAW）
     */
    private FundFlowTypeEnum fundFlowType;

    /**
     * 币种，如 USDT、ETH，过账时填充凭证币种
     */
    private String currency;
}