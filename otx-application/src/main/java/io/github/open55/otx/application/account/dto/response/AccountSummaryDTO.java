package io.github.open55.otx.application.account.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户摘要 DTO，管理控制台账户总览页的数据载体。
 * <p>
 * 仅承载列表页展示所需的精简字段，避免将完整账户实体暴露给页面层。
 */
@Data
public class AccountSummaryDTO {

    /**
     * 用户唯一标识，作为账户的业务键，同时是跳转资金流水页的链接参数
     */
    private Long uid;

    /**
     * 可用余额，用户可自由支配的资金，金额单位与币种一致
     */
    private BigDecimal availableBalance;

    /**
     * 冻结余额，提现中或锁定中的资金，与可用余额之和为用户总资产
     */
    private BigDecimal frozenBalance;
}
