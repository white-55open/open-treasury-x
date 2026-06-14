package io.github.open55.otx.application.deposit.dto;

import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChangeAmountRequest {

    private Long uid;

    private BigDecimal amount;

    private String bizNo;

    private FundFlowTypeEnum fundFlowType;
}