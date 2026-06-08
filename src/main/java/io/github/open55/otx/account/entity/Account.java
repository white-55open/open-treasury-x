package io.github.open55.otx.account.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("account")
@Data
public class Account {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long uid;

    private BigDecimal availableBalance;

    private BigDecimal frozenBalance;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}