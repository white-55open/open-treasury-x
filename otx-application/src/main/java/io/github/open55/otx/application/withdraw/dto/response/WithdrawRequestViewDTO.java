package io.github.open55.otx.application.withdraw.dto.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现请求视图 DTO，管理控制台提现单据列表页的数据载体。
 * <p>
 * 展示提现单据的业务要素与状态机，供运维人员在页面上跟踪与操作提现流程。
 */
@Data
public class WithdrawRequestViewDTO {

    /**
     * 业务流水号，用作幂等键，全局唯一，也是结算/取消操作表单的提交参数
     */
    private String bizNo;

    /**
     * 用户唯一标识，提现发起人，作为查询与风控的业务键
     */
    private Long uid;

    /**
     * 提现金额，必须为正数，与冻结金额一致
     */
    private BigDecimal amount;

    /**
     * 币种，如 USDT、ETH
     */
    private String currency;

    /**
     * 区块链 ID，如 "1"（以太坊主网）、"11155111"（Sepolia 测试网）
     */
    private String chainId;

    /**
     * 接收方地址，用户提现的目标链上地址
     */
    private String toAddress;

    /**
     * 代币合约地址，ERC-20 代币提现时必填；原生币提现时为空
     */
    private String tokenAddress;

    /**
     * 链上交易哈希，广播成功后回填；未广播时为空
     */
    private String txHash;

    /**
     * 提现请求状态：PENDING / BROADCASTED / SETTLED / FAILED / CANCELLED
     */
    private String status;
}
