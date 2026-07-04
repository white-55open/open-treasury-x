package io.github.open55.otx.domain.ledger.port;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigInteger;

/**
 * 链上交易回执值对象。
 * <p>
 * 包含链上交易的核心信息：链 ID、交易哈希、区块高度、交易状态、确认数、发送方、接收方、交易金额。
 * 作为 ChainQueryPort.queryTxReceipt() 的返回值。
 */
@Getter
@RequiredArgsConstructor
public class ChainTxReceipt {

    /**
     * 区块链 ID
     */
    private final String chainId;

    /**
     * 交易哈希
     */
    private final String txHash;

    /**
     * 交易所在区块高度
     */
    private final BigInteger blockNumber;

    /**
     * 交易状态（0x0=失败, 0x1=成功）
     */
    private final String status;

    /**
     * 当前确认数
     */
    private final int confirmations;

    /**
     * 发送方地址
     */
    private final String from;

    /**
     * 接收方地址
     */
    private final String to;

    /**
     * 交易金额（Wei）
     */
    private final BigInteger value;
}
