package io.github.open55.otx.account.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.github.open55.otx.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@TableName("account_t")
@Data
public class AccountEntity extends BaseEntity {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    private BigDecimal availableBalance;

    private BigDecimal frozenBalance;
}