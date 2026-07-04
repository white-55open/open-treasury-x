package io.github.open55.otx.application.ledger.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 凭证详情响应 DTO，对应 GET /ledger/journals/{bizNo} 和 POST /ledger/journals 的响应体
 */
@Data
public class JournalDetailResponseDTO {

    /**
     * 凭证 ID，数据库主键
     */
    private Long id;

    /**
     * 业务流水号，用作幂等键，全局唯一
     */
    private String bizNo;

    /**
     * 业务类型，标识该凭证的业务来源
     */
    private String bizType;

    /**
     * 币种，如 USDT、ETH
     */
    private String currency;

    /**
     * 凭证状态：DRAFT（草稿）、POSTED（已过账）、REVERSED（已冲销）
     */
    private String status;

    /**
     * 凭证总金额，所有分录金额之和
     */
    private BigDecimal totalAmount;

    /**
     * 记账日期，业务实际发生日期（非系统日期）
     */
    private LocalDate postingDate;

    /**
     * 业务描述，说明该凭证的业务摘要
     */
    private String description;

    /**
     * 区块链 ID，如 "1"（以太坊主网），链下业务为空
     */
    private String chainId;

    /**
     * 链上交易哈希，链下业务为空
     */
    private String chainTxHash;

    /**
     * 区块高度，链下业务为空
     */
    private Long blockNumber;

    /**
     * 代币合约地址，链下业务为空
     */
    private String tokenAddress;

    /**
     * 链上确认数，链下业务为空
     */
    private Integer confirmations;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 借贷分录列表
     */
    private List<LedgerEntryResponseDTO> entries;
}
