package io.github.open55.otx.domain.ledger.port;

import java.util.Optional;

/**
 * 链上查询出站端口，预留供后续 web3j 适配器实现。
 * <p>
 * 提供链上交易回执查询、当前区块高度获取、确认数判断三个方法。
 * 本期仅定义接口，不写实现类。
 */
public interface ChainQueryPort {

    /**
     * 查询链上交易回执。
     *
     * @param chainId 区块链 ID
     * @param txHash  交易哈希
     * @return 交易回执，链上查不到时返回 Optional.empty()
     */
    Optional<ChainTxReceipt> queryTxReceipt(String chainId, String txHash);

    /**
     * 获取指定链的当前区块高度。
     *
     * @param chainId 区块链 ID
     * @return 当前区块高度
     */
    Long currentBlockNumber(String chainId);

    /**
     * 判断指定交易是否已满足确认数要求。
     *
     * @param chainId             区块链 ID
     * @param txHash              交易哈希
     * @param requiredConfirmations 所需最小确认数
     * @return true 表示确认数 ≥ requiredConfirmations
     */
    boolean isConfirmed(String chainId, String txHash, int requiredConfirmations);
}
