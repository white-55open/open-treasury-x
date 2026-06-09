package io.github.open55.otx.fundflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.open55.otx.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@TableName("fund_flow_t")
@Data
public class FundFlowEntity extends BaseEntity {

    private String flowNo;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    private String bizNo;

    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private String direction;

    private String type;
}