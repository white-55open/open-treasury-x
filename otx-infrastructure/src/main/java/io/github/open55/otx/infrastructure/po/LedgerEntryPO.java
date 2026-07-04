package io.github.open55.otx.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 分录值对象持久化对象。
 * <p>
 * 对应数据库表 ledger_entry_t，字段与 V3__ledger_entry.sql 一致。
 * 枚举字段在 PO 层使用 String 类型，由 Converter 负责 String ↔ Enum 互转。
 */
@EqualsAndHashCode(callSuper = true)
@TableName("ledger_entry_t")
@Data
public class LedgerEntryPO extends BasePO {

    /**
     * 关联凭证 ID
     */
    private Long journalId;

    /**
     * 业务流水号（冗余，用于按 bizNo 查询时避免 join）
     */
    private String bizNo;

    /**
     * 会计科目编码
     */
    private String accountCode;

    /**
     * 分录类型（DEBIT 借方 / CREDIT 贷方）
     */
    private String entryType;

    /**
     * 分录金额，必须为正数
     */
    private BigDecimal amount;

    /**
     * 用户唯一标识
     */
    private Long uid;

    /**
     * 交易对手地址或标识
     */
    private String counterparty;

    /**
     * 记账后该账户余额
     */
    private BigDecimal balanceAfter;

    /**
     * 备注说明
     */
    private String remark;
}
