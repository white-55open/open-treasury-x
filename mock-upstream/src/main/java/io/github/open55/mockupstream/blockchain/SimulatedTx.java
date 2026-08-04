package io.github.open55.mockupstream.blockchain;

import lombok.Data;

import java.math.BigInteger;

/**
 * 模拟链上交易（内存模型）。
 * <p>
 * 等价于真实链上的一笔交易：包含发送方、接收方、金额（以最小单位 wei 计）、
 * 打包区块高度与链上状态。创建后不可变，确认数由 {@link BlockchainSimulator}
 * 依据当前区块高度动态计算，不落库、不连真实 RPC。
 */
@Data
public class SimulatedTx {

    /**
     * 交易哈希，链上交易的唯一标识（"0x" + 32 字节随机 hex）
     */
    private final String txHash;

    /**
     * 发送方地址（模拟热钱包地址）
     */
    private final String from;

    /**
     * 接收方地址（模拟用户充值地址）
     */
    private final String to;

    /**
     * 转账金额（最小单位 wei，如 USDT 6 位小数则 100 USDT = 100000000 wei）
     */
    private final BigInteger amountWei;

    /**
     * 交易打包所在区块高度，用于计算确认数（currentBlock - createdBlock + 1）
     */
    private final long createdBlock;

    /**
     * 链上交易状态，模拟成功回执状态 "0x1"（等价真实链 1 = SUCCESS）
     */
    private final String status;
}
