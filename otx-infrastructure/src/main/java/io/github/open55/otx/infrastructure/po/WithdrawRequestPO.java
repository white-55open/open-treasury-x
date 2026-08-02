package io.github.open55.otx.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 提现请求聚合根持久化对象。
 * <p>
 * 对应数据库表 withdraw_request_t，字段与 V5__withdraw_request.sql 一致。
 * 枚举字段 status 在 PO 层使用 String 类型，由 Converter 负责 String ↔ Enum 互转。
 */
@EqualsAndHashCode(callSuper = true)
@TableName("withdraw_request_t")
@Data
public class WithdrawRequestPO extends BasePO {

    /**
     * 用户唯一标识，提现发起人
     */
    private Long uid;

    /**
     * 业务流水号，用作幂等键
     */
    private String bizNo;

    /**
     * 提现金额，与冻结金额一致
     */
    private BigDecimal amount;

    /**
     * 币种
     */
    private String currency;

    /**
     * 区块链 ID
     */
    private String chainId;

    /**
     * 接收方地址
     */
    private String toAddress;

    /**
     * 代币合约地址，原生币提现时为空
     */
    private String tokenAddress;

    /**
     * 链上交易哈希，广播成功后回填
     */
    private String txHash;

    /**
     * 提现请求状态
     */
    private String status;
}
