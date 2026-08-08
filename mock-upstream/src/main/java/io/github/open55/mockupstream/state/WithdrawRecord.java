package io.github.open55.mockupstream.state;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟提现记录（内存模型）。
 * <p>
 * 对应一次"用户申请提现"的完整生命周期：申请时调用 OTX 冻结资金、
 * 状态为 FROZEN（已冻结）；「一键处理」对 FROZEN 记录推进广播与确认结算
 * （→ SETTLED），对标记取消的 CANCEL_PENDING 记录完成解冻（→ CANCELLED）。
 * 链下冻结与链上请求使用不同业务号（freezeBizNo / bizNo），与 OTX 幂等契约一致。
 */
@Data
public class WithdrawRecord {

    /**
     * 提现记录状态枚举
     */
    public enum Status {

        /**
         * 已冻结：冻结资金完成，等待一键处理广播与结算
         */
        FROZEN,

        /**
         * 已结算：链上确认达标，冻结资金扣减完成
         */
        SETTLED,

        /**
         * 取消处理中：用户已标记取消，等待一键处理完成解冻
         */
        CANCEL_PENDING,

        /**
         * 已取消：解冻完成，资金退回可用余额
         */
        CANCELLED
    }

    /**
     * 提现请求业务号（bizNo），用于广播与确认结算/取消端点
     */
    private final String bizNo;

    /**
     * 链下冻结业务号（freezeBizNo），独立于链上请求号（OTX 幂等契约）
     */
    private final String freezeBizNo;

    /**
     * 提现用户标识，归属的用户
     */
    private final Long uid;

    /**
     * 提现金额（展示单位，如 50 USDT）
     */
    private final BigDecimal amount;

    /**
     * 提现币种（如 USDT）
     */
    private final String currency;

    /**
     * 用户提现目标地址（模拟外部地址）
     */
    private final String toAddress;

    /**
     * 提现记录创建时间戳（毫秒），用于记录列表排序
     */
    private final long createdAt;

    /**
     * 提现记录当前状态（可变：FROZEN → SETTLED / CANCEL_PENDING → CANCELLED）
     */
    private Status status;
}
