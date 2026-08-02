package io.github.open55.otx.domain.chain.port;

import lombok.Getter;

/**
 * 已签名交易值对象：广播入参。
 * <p>
 * 由 {@link SignerPort} 签名完成后返回，作为 {@link TxBroadcastPort#broadcast(SignedTx)}
 * 的输入。构造时校验 rawTransaction 非空非空白，确保广播的数据始终有效。
 */
@Getter
public class SignedTx {

    /**
     * 区块链 ID
     * <p>与签名请求保持一致，用于广播适配器选择正确的 RPC 节点</p>
     */
    private final String chainId;

    /**
     * 十六进制签名字符串（0x 前缀）
     * <p>签名设施产出的 RLP 编码原始交易，可直接提交 eth_sendRawTransaction</p>
     */
    private final String rawTransaction;

    /**
     * 发送方地址
     * <p>交易签名对应的钱包地址</p>
     */
    private final String fromAddress;

    /**
     * 接收方地址
     * <p>交易的目标地址</p>
     */
    private final String toAddress;

    /**
     * 构造 SignedTx，执行自校验确保业务不变量。
     *
     * @param chainId        区块链 ID
     * @param rawTransaction 十六进制签名字符串，不能为空或空白
     * @param fromAddress    发送方地址
     * @param toAddress      接收方地址
     * @throws IllegalArgumentException 当 rawTransaction 为空或空白时抛出
     */
    public SignedTx(String chainId, String rawTransaction, String fromAddress, String toAddress) {
        // 校验原始交易串：必填，不允许空白（空签名结果无法广播）
        if (rawTransaction == null || rawTransaction.isBlank()) {
            throw new IllegalArgumentException("rawTransaction must not be null or blank");
        }
        this.chainId = chainId;
        this.rawTransaction = rawTransaction;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
    }
}
