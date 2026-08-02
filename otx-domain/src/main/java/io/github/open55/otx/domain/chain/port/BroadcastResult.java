package io.github.open55.otx.domain.chain.port;

import lombok.Getter;

/**
 * 广播结果值对象。
 * <p>
 * 由 {@link TxBroadcastPort#broadcast(SignedTx)} 返回，包含链 ID、交易哈希、
 * 发送方与接收方地址。构造时校验 txHash 非空非空白，确保广播成功的结果始终可留痕。
 */
@Getter
public class BroadcastResult {

    /**
     * 区块链 ID
     * <p>广播交易所在的链，用于后续确认查询与凭证留痕</p>
     */
    private final String chainId;

    /**
     * 链上交易哈希
     * <p>广播成功后的交易唯一标识，用于确认结算与审计</p>
     */
    private final String txHash;

    /**
     * 发送方地址
     * <p>交易 from 地址</p>
     */
    private final String fromAddress;

    /**
     * 接收方地址
     * <p>交易 to 地址</p>
     */
    private final String toAddress;

    /**
     * 构造 BroadcastResult，执行自校验确保业务不变量。
     *
     * @param chainId     区块链 ID
     * @param txHash      链上交易哈希，不能为空或空白
     * @param fromAddress 发送方地址
     * @param toAddress   接收方地址
     * @throws IllegalArgumentException 当 txHash 为空或空白时抛出
     */
    public BroadcastResult(String chainId, String txHash, String fromAddress, String toAddress) {
        // 校验交易哈希：必填，不允许空白（无哈希的广播结果无法用于确认结算）
        if (txHash == null || txHash.isBlank()) {
            throw new IllegalArgumentException("txHash must not be null or blank");
        }
        this.chainId = chainId;
        this.txHash = txHash;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
    }
}
