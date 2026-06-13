package io.github.open55.otx.application.deposit.service.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateDepositRequest {

    private Long uid;

    private BigDecimal amount;

    private String bizNo;
}