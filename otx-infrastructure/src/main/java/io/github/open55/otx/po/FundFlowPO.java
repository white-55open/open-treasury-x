package io.github.open55.otx.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@TableName("fund_flow_t")
@Data
public class FundFlowPO extends BasePO {

    private String flowNo;

    private Long uid;

    private String bizNo;

    private BigDecimal amount;

    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;

    private String direction;

    private String type;
}