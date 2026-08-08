package io.github.open55.mockupstream.state;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟充值记录（内存模型）。
 * <p>
 * 对应一次"用户向平台充值"的完整生命周期：发起时在模拟链注册交易、
 * 状态为 CONFIRMING（确认中，确认数由模拟链动态计算）；「一键处理」推进
 * 确认达标后调用 OTX 入账，状态置为 BOOKED（已入账）。
 * 每笔充值独立业务号（bizNo）、独立交易哈希，互不干扰。
 */
@Data
public class DepositRecord {

    /**
     * 充值记录状态枚举
     */
    public enum Status {

        /**
         * 确认中：交易已在模拟链打包，确认数尚未达到入账阈值
         */
        CONFIRMING,

        /**
         * 已入账：确认达标且 OTX 已成功入账（POST /deposit 成功）
         */
        BOOKED
    }

    /**
     * 充值业务号（bizNo），作为 OTX 幂等键与记录唯一标识
     */
    private final String bizNo;

    /**
     * 充值用户标识，归属的用户
     */
    private final Long uid;

    /**
     * 充值金额（展示单位，如 100 USDT）
     */
    private final BigDecimal amount;

    /**
     * 充值币种（如 USDT）
     */
    private final String currency;

    /**
     * 模拟链交易哈希，OTX 入账时作为 chainTxHash 传递
     */
    private final String txHash;

    /**
     * 充值记录创建时间戳（毫秒），用于记录列表排序
     */
    private final long createdAt;

    /**
     * 充值记录当前状态（可变：CONFIRMING → BOOKED）
     */
    private Status status;
}
