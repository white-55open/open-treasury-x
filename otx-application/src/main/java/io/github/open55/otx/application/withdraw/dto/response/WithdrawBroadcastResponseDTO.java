package io.github.open55.otx.application.withdraw.dto.response;

import lombok.Data;

/**
 * 提现广播响应 DTO，对应 POST /withdraw/broadcast 的响应体。
 * <p>
 * 广播成功或幂等返回时携带业务流水号、链上交易哈希与请求状态，
 * 供上游确认广播结果并跟踪后续结算进度。
 */
@Data
public class WithdrawBroadcastResponseDTO {

    /**
     * 业务流水号，幂等键，全局唯一
     */
    private String bizNo;

    /**
     * 链上交易哈希，广播成功后有值；幂等返回时取已有请求的交易哈希
     */
    private String txHash;

    /**
     * 提现请求状态：PENDING / BROADCASTED / SETTLED / FAILED / CANCELLED
     */
    private String status;
}
