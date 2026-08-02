package io.github.open55.otx.application.withdraw.dto.request;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 提现请求 DTO，供冻结、结算、解冻与链上广播四个用例共用。
 * <p>
 * 携带用户标识、业务流水号、金额与币种四要素（冻结/结算/解冻用例），
 * 以及区块链 ID、接收方地址、代币地址与确认数等链上要素（广播用例），
 * 其中业务流水号（bizNo）作为各用例的幂等键。
 */
@Data
public class WithdrawRequestDTO {

    /**
     * 用户唯一标识，资金变动的主体
     */
    private Long uid;

    /**
     * 业务流水号，用作幂等键，全局唯一
     */
    private String bizNo;

    /**
     * 变动金额，必须大于零
     */
    private BigDecimal amount;

    /**
     * 币种，如 USDT、ETH
     */
    private String currency;

    /**
     * 区块链 ID，如 "1"（以太坊主网）、"11155111"（Sepolia 测试网）
     * <p>广播用例必填，用于签名适配器选择链参数与广播端口选择 RPC 节点</p>
     */
    private String chainId;

    /**
     * 接收方地址，用户提现的目标链上地址
     * <p>广播用例必填，作为转账交易的 to 地址</p>
     */
    private String toAddress;

    /**
     * 代币合约地址，ERC-20 代币提现时必填；原生币提现时为空
     */
    private String tokenAddress;

    /**
     * 链上确认数，广播时可选；为空时结算阶段取系统配置的默认确认数
     */
    private Integer requiredConfirmations;
}
