package io.github.open55.mockupstream.web;

import io.github.open55.mockupstream.state.DepositRecord;

import java.math.BigDecimal;

/**
 * 充值记录视图（供个人中心记录列表展示）。
 * <p>
 * 在充值记录基础上附加动态确认数（由模拟链实时计算）与所需确认数，
 * 页面展示"当前确认数/所需确认数"的确认演化状态。
 *
 * @param bizNo                充值业务号
 * @param amount               充值金额
 * @param currency             充值币种
 * @param status               记录状态（CONFIRMING/BOOKED）
 * @param confirmations        当前链上确认数（动态计算）
 * @param requiredConfirmations 入账所需确认数
 */
public record DepositView(String bizNo, BigDecimal amount, String currency, DepositRecord.Status status,
                          long confirmations, int requiredConfirmations) {

    /**
     * 由充值记录与模拟链动态确认数组装视图。
     *
     * @param record               充值记录
     * @param confirmations        当前确认数
     * @param requiredConfirmations 所需确认数
     * @return 充值记录视图
     */
    public static DepositView from(DepositRecord record, long confirmations, int requiredConfirmations) {
        return new DepositView(record.getBizNo(), record.getAmount(), record.getCurrency(),
                record.getStatus(), confirmations, requiredConfirmations);
    }
}
