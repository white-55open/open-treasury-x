package io.github.open55.otx.domain.withdraw;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 提现请求状态枚举。
 * <p>
 * 描述提现请求从创建到终态的生命周期：
 * PENDING（待广播）→ BROADCASTED（已广播）→ SETTLED（已结算）/ FAILED（失败）/ CANCELLED（已取消）。
 * 状态转换只能通过 {@link WithdrawRequestEntity} 的领域方法执行，非法转移抛 WITHDRAW_REQUEST_STATUS_INVALID。
 */
@Getter
@RequiredArgsConstructor
public enum WithdrawRequestStatusEnum {

    /**
     * 待广播：提现请求已创建，尚未提交链上
     */
    PENDING("PENDING"),

    /**
     * 已广播：交易已提交链上并取得交易哈希，等待确认结算
     */
    BROADCASTED("BROADCASTED"),

    /**
     * 已结算：链上确认达标，冻结资金已扣除并完成过账
     */
    SETTLED("SETTLED"),

    /**
     * 已失败：签名/广播失败或链上交易失败，资金保持冻结待处理
     */
    FAILED("FAILED"),

    /**
     * 已取消：提现被取消，冻结资金已解冻退回
     */
    CANCELLED("CANCELLED");

    /**
     * 状态码，与枚举字面量一致，持久化到数据库 status 列
     */
    private final String code;
}
