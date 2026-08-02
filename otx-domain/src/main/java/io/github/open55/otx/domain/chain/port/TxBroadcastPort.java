package io.github.open55.otx.domain.chain.port;

import java.math.BigInteger;

/**
 * 广播出站端口（Outbound Port）。
 * <p>
 * 负责将已签名交易提交到链上并获取交易哈希，以及辅助查询发送方当前 nonce。
 * 实现可插拔：本期仅提供 web3j 实现（多 RPC 按序故障切换），
 * 未来可替换为其他链接入方案。由提现广播编排、资金归集等横切场景复用。
 */
public interface TxBroadcastPort {

    /**
     * 广播已签名交易到链上。
     *
     * @param signedTx 已签名交易，rawTransaction 非空
     * @return 广播结果，包含链上交易哈希
     */
    BroadcastResult broadcast(SignedTx signedTx);

    /**
     * 查询发送方地址在指定链上的当前 nonce。
     * <p>
     * 辅助方法：用于广播前确定交易 nonce，避免 nonce 冲突导致交易被拒。
     *
     * @param chainId     区块链 ID
     * @param fromAddress 发送方地址
     * @return 当前 nonce（含待确认交易的 PENDING 计数）
     */
    BigInteger currentNonce(String chainId, String fromAddress);
}
