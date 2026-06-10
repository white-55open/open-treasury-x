package io.github.open55.otx.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.open55.otx.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@TableName("account_t")
@Data
public class AccountDO extends BaseDO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    private BigDecimal availableBalance;

    private BigDecimal frozenBalance;
}