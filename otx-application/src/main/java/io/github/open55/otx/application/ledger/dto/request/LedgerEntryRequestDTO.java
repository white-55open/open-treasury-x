package io.github.open55.otx.application.ledger.dto.request;

import io.github.open55.otx.domain.ledger.enums.LedgerAccountCodeEnum;
import io.github.open55.otx.domain.ledger.enums.LedgerEntryTypeEnum;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 分录规格 DTO，描述单条借贷分录的属性
 */
@Data
public class LedgerEntryRequestDTO {

    /**
     * 会计科目编码，标识分录所属的系统账户
     */
    private LedgerAccountCodeEnum accountCode;

    /**
     * 分录类型：DEBIT（借方）或 CREDIT（贷方）
     */
    private LedgerEntryTypeEnum entryType;

    /**
     * 分录金额，必须为正数
     */
    private BigDecimal amount;

    /**
     * 用户唯一标识，平台类账户此字段可为空
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
