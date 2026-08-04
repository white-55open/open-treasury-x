package io.github.open55.otx.application.ledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 凭证摘要 DTO，管理控制台凭证列表页的数据载体。
 * <p>
 * 仅承载凭证主表字段（不含分录），列表页保持轻量，分录详情走凭证详情查询。
 */
@Data
public class JournalSummaryDTO {

    /**
     * 业务流水号，用作幂等键，全局唯一，同时是跳转凭证详情页的链接参数
     */
    private String bizNo;

    /**
     * 业务类型，标识该凭证的业务来源（充值、提现、冻结、解冻等）
     */
    private String bizType;

    /**
     * 凭证状态：DRAFT（草稿）、POSTED（已过账）、REVERSED（已冲销）
     */
    private String status;

    /**
     * 凭证总金额，所有分录金额之和（借方金额之和 = 贷方金额之和）
     */
    private BigDecimal totalAmount;

    /**
     * 记账日期，业务实际发生日期（非系统日期）
     */
    private LocalDate postingDate;

    /**
     * 链上交易哈希，链下业务为空
     */
    private String chainTxHash;
}
