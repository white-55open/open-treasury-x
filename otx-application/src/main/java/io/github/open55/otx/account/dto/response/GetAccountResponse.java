package io.github.open55.otx.account.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GetAccountResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uid;

    private BigDecimal availableBalance;

    private BigDecimal frozenBalance;
}
