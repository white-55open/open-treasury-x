package io.github.open55.otx.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 凭证聚合根持久化对象。
 * <p>
 * 对应数据库表 ledger_journal_t，字段与 V2__ledger.sql 一致。
 * 枚举字段在 PO 层使用 String 类型，由 Converter 负责 String ↔ Enum 互转。
 */
@EqualsAndHashCode(callSuper = true)
@TableName("ledger_journal_t")
@Data
public class LedgerJournalPO extends BasePO {

    /**
     * 业务流水号，用作幂等键
     */
    private String bizNo;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 记账日期
     */
    private LocalDate postingDate;

    /**
     * 币种
     */
    private String currency;

    /**
     * 凭证状态
     */
    private String status;

    /**
     * 凭证总金额
     */
    private BigDecimal totalAmount;

    /**
     * 业务描述
     */
    private String description;

    /**
     * 区块链 ID
     */
    private String chainId;

    /**
     * 链上交易哈希
     */
    private String chainTxHash;

    /**
     * 区块高度
     */
    private Long blockNumber;

    /**
     * 代币合约地址
     */
    private String tokenAddress;

    /**
     * 链上确认数
     */
    private Integer confirmations;

    /**
     * 被冲销时记录的反向凭证业务号
     */
    private Long reversedBy;
}
