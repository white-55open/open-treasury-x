package io.github.open55.otx.application.deposit.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 充值入账请求 DTO，继承资金变更请求的通用字段。
 * <p>
 * 在 ChangeAmountRequest 基础上新增链上证据字段：充值入账前必须携带
 * 区块链 ID 与链上交易哈希，由应用层确认闸门校验链上交易已达到安全确认数；
 * requiredConfirmations 与 tokenAddress 为可选字段，分别用于单笔覆盖
 * 默认确认数与预留 ERC-20 代币充值场景。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepositRequestDTO extends ChangeAmountRequest {

    /**
     * 区块链 ID，如 "1"（以太坊主网）、"11155111"（Sepolia 测试网）。
     * <p>
     * 必填：缺失或空白时应用层抛 DEPOSIT_CHAIN_INFO_MISS 拒绝入账，
     * 用于确认闸门选择链参数与链上查询端口路由 RPC 节点。
     */
    private String chainId;

    /**
     * 链上交易哈希，充值交易在链上的唯一标识。
     * <p>
     * 必填：缺失或空白时应用层抛 DEPOSIT_CHAIN_INFO_MISS 拒绝入账，
     * 确认闸门以此查询交易确认数与回执区块高度。
     */
    private String chainTxHash;

    /**
     * 所需安全确认数，可选，必须为正整数。
     * <p>
     * 缺省时取配置项 web3j.required-confirmations 的默认值（12）；
     * 单笔请求可覆盖默认值，如测试网确认产出慢时临时调低。
     */
    private Integer requiredConfirmations;

    /**
     * 代币合约地址，可选。
     * <p>
     * 原生币（如 ETH）充值场景为空；ERC-20 代币充值场景由上游携带，
     * 预留供对账模块后续从回执日志解析核对，当前仅随凭证留痕。
     */
    private String tokenAddress;
}
