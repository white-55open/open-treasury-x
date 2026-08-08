package io.github.open55.mockupstream.blockchain;

import io.github.open55.mockupstream.config.SimulatorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 模拟区块链（内存状态机）。
 * <p>
 * 维护一个随时间推进的模拟链：区块高度仅由「一键处理」按需推进
 * （{@link #advanceTo(long)}），交易确认数随区块推进递增。发起充值时在链上
 * 注册交易，确认数达标与否由 {@link #confirmationsOf(String)} 动态计算，
 * 页面实时展示"当前确认数/所需确认数"。不落库、不连真实 RPC。
 * <p>
 * 与原实现的差异：移除定时自动推块（@Scheduled）与确认达标回调机制——
 * 入账触发改为由 ProcessOrchestrator 直接驱动（确认达标后调用 OTX），
 * 从而支持"一键处理失败后重试"（回调一次性触发无法重试）。
 */
@Slf4j
@Component
public class BlockchainSimulator {

    /**
     * 当前区块高度，从 0 开始递增
     */
    private final AtomicLong currentBlockNumber = new AtomicLong(0);

    /**
     * 交易注册表：txHash → 模拟交易
     */
    private final Map<String, SimulatedTx> transactions = new ConcurrentHashMap<>();

    /**
     * 随机源，用于生成交易哈希
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 模拟器配置（确认阈值等）
     */
    private final SimulatorProperties properties;

    /**
     * 构造模拟链，注入模拟器配置。
     *
     * @param properties 模拟器配置
     */
    public BlockchainSimulator(SimulatorProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册一笔模拟充值交易，打包在当前区块高度。
     *
     * @param from      发送方地址（模拟热钱包）
     * @param to        接收方地址（模拟用户地址）
     * @param amountWei 转账金额（最小单位 wei）
     * @return 新建的模拟交易（含生成的交易哈希）
     */
    public SimulatedTx registerTx(String from, String to, BigInteger amountWei) {
        String txHash = randomTxHash();
        SimulatedTx tx = new SimulatedTx(txHash, from, to, amountWei, currentBlockNumber.get(), "0x1");
        transactions.put(txHash, tx);
        log.info("[MockChain] registered tx {}: from={} -> to={}, amount={} wei, packed at block #{}, confirmations 1/{}",
                txHash, from, to, amountWei, tx.getCreatedBlock(), properties.getRequiredConfirmations());
        return tx;
    }

    /**
     * 计算指定交易的当前确认数：currentBlock - createdBlock + 1，下限为 0。
     *
     * @param txHash 交易哈希
     * @return 当前确认数（交易不存在时为 0）
     */
    public long confirmationsOf(String txHash) {
        SimulatedTx tx = transactions.get(txHash);
        if (tx == null) {
            return 0;
        }
        return Math.max(0, currentBlockNumber.get() - tx.getCreatedBlock() + 1);
    }

    /**
     * 当前区块高度（供编排器计算推进目标高度）。
     *
     * @return 当前区块高度
     */
    public long currentHeight() {
        return currentBlockNumber.get();
    }

    /**
     * 按需推进区块至目标高度（幂等：只增不减）。
     * <p>
     * 仅在「一键处理」时调用：将所有在链交易的确认数推进到位，
     * 每推进一块打印当前高度与各交易确认数，供页面日志展示确认数演化过程。
     *
     * @param targetHeight 目标区块高度（小于等于当前高度时不做任何推进）
     * @return 实际推进后的最新区块高度
     */
    public long advanceTo(long targetHeight) {
        while (currentBlockNumber.get() < targetHeight) {
            long height = currentBlockNumber.incrementAndGet();
            log.info("[MockChain] block advanced to #{}, {} tx(s) on chain", height, transactions.size());
            transactions.values().forEach(tx -> log.info("[MockChain]   tx {} confirmations {}/{}",
                    tx.getTxHash(), confirmationsOf(tx.getTxHash()), properties.getRequiredConfirmations()));
        }
        return currentBlockNumber.get();
    }

    /**
     * 生成模拟交易哈希："0x" + 32 字节随机 hex（等价真实链 64 位十六进制哈希）。
     *
     * @return 模拟交易哈希
     */
    private String randomTxHash() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder hex = new StringBuilder("0x");
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
