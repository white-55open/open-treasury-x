package io.github.open55.otx.application.withdraw.dto.response;

import lombok.Data;

/**
 * 提现确认结算响应 DTO，对应 POST /withdraw/{bizNo}/confirm-settle 的响应体。
 * <p>
 * 链上确认达标后结算完成时返回，携带业务流水号、提现请求状态与凭证状态，
 * 供上游确认资金结算与总账过账结果。
 */
@Data
public class WithdrawSettleResponseDTO {

    /**
     * 业务流水号，幂等键，全局唯一
     */
    private String bizNo;

    /**
     * 提现请求状态，结算成功为 SETTLED
     */
    private String status;

    /**
     * 凭证状态，草稿凭证过账后为 POSTED（过账失败时交由对账修复，仍按 POSTED 返回）
     */
    private String journalStatus;
}
