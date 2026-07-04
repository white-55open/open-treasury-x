package io.github.open55.otx.application.account.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户详情响应。
 */
@Data
public class GetAccountResponse {
    /**
     * 用户唯一标识
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    /**
     * 可用余额
     */
    private BigDecimal availableBalance;

    /**
     * 冻结余额
     */
    private BigDecimal frozenBalance;
}
