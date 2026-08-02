package io.github.open55.otx.application.withdraw.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现状态响应 DTO，对应 POST /withdraw/{bizNo}/cancel 与 GET /withdraw/{bizNo}/status 的响应体。
 * <p>
 * 携带提现请求的业务要素与当前状态，供上游查询进度或确认取消结果。
 */
@Data
public class WithdrawStatusResponseDTO {

    /**
     * 业务流水号，幂等键，全局唯一
     */
    private String bizNo;

    /**
     * 提现请求状态：PENDING / BROADCASTED / SETTLED / FAILED / CANCELLED
     */
    private String status;

    /**
     * 链上交易哈希，已广播后有值；未广播时为空
     */
    private String txHash;

    /**
     * 提现金额，与冻结时一致
     */
    private BigDecimal amount;

    /**
     * 币种，如 USDT、ETH
     */
    private String currency;
}
