package io.github.open55.otx.application.ledger.dto.request;

import io.github.open55.otx.domain.ledger.enums.LedgerBizTypeEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 过账请求 DTO，对应 POST /ledger/journals 的请求体
 */
@Data
public class PostJournalRequestDTO {

    /**
     * 业务流水号，用作幂等键，全局唯一
     */
    private String bizNo;

    /**
     * 业务类型，标识该凭证的业务来源（充值、提现、内部转账等）
     */
    private LedgerBizTypeEnum bizType;

    /**
     * 币种，如 USDT、ETH
     */
    private String currency;

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
     * 借贷分录列表，至少包含一条 DEBIT 和一条 CREDIT
     */
    private List<LedgerEntryRequestDTO> entries;
}
