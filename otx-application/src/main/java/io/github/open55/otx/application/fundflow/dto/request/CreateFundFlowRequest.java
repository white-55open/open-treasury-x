package io.github.open55.otx.application.fundflow.dto.request;

import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFundFlowRequest {

    private Long uid;

    private String bizNo;

    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private FundFlowDirectionEnum direction;

    private FundFlowTypeEnum type;
}