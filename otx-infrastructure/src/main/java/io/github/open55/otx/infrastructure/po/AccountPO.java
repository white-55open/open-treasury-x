package io.github.open55.otx.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@TableName("account_t")
@Data
public class AccountPO extends BasePO {

    private Long uid;

    private BigDecimal availableBalance;

    private BigDecimal frozenBalance;
}