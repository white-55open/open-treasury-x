package io.github.open55.otx.domain.fundflow.entity;

import io.github.open55.otx.domain.common.entity.BaseEntity;
import io.github.open55.otx.domain.fundflow.enums.FundFlowDirectionEnum;
import io.github.open55.otx.domain.fundflow.enums.FundFlowTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
public class FundFlowEntity extends BaseEntity {
    private String flowNo;

    private Long uid;

    private String bizNo;

    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private FundFlowDirectionEnum direction;

    private FundFlowTypeEnum type;
}
