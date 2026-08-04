package io.github.open55.mockupstream.blockchain;

import io.github.open55.mockupstream.config.SimulatorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 模拟区块链（内存状态机）。
 * <p>
 * 维护一个随时间增长的模拟链：区块高度每 {@code block-interval-ms} 推进一块，
 * 链上交易的确认数随之递增。当某笔交易确认数首次达到 OTX 配置的安全确认阈值时，
 * 触发已注册的确认达标回调（由充值故事注册，回调内调用 OTX POST /deposit），
 * 从而真实还原"链上事件 → 确认数演化 → 业务系统通知入账"的完整链条。
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
     * 已触发确认回调的交易集合（防止同一交易重复触发回调、重复入账）
     */
    private final Set<String> callbackTriggered = ConcurrentHashMap.newKeySet();

    /**
     * 确认达标回调列表：交易首次达到所需确认数时逐个执行（充值故事注册）
     */
    private final List<Consumer<SimulatedTx>> confirmationCallbacks = new CopyOnWriteArrayList<>();

    /**
     * 模拟器配置（确认阈值、区块间隔等）
     */
    private final SimulatorProperties properties;

    /**
     * 随机源，用于生成交易哈希
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造模拟链，注入模拟器配置。
     *
     * @param properties 模拟器配置
     */
    public BlockchainSimulator(SimulatorProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册确认达标回调：某交易确认数首次达到阈值时执行（幂等触发，仅一次）。
     *
     * @param callback 确认达标回调，入参为达标的模拟交易
     */
    public void onConfirmationsMet(Consumer<SimulatedTx> callback) {
        confirmationCallbacks.add(callback);
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
        log.info("[模拟链] 注册交易 {}：from={} → to={}，金额 {} wei，打包于区块 #{}，当前确认数 1/{}",
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
     * 定时推进区块：当前高度 +1，并打印各在链交易的最新确认数；
     * 对首次达到确认阈值的交易触发确认达标回调（防重复触发）。
     */
    @Scheduled(fixedDelayString = "${otx.block-interval-ms:2000}")
    public void advanceBlock() {
        long height = currentBlockNumber.incrementAndGet();
        log.info("[模拟链] 区块推进至 #{}，在链交易 {} 笔", height, transactions.size());
        transactions.values().forEach(tx -> {
            long confirmations = confirmationsOf(tx.getTxHash());
            log.info("[模拟链]   交易 {} 确认数 {}/{}", tx.getTxHash(), confirmations, properties.getRequiredConfirmations());
            // 首次达标才触发回调，putIfAbsent 防并发重复触发
            if (confirmations >= properties.getRequiredConfirmations()
                    && callbackTriggered.add(tx.getTxHash())) {
                log.info("[模拟链] 交易 {} 确认达标（{} ≥ {}），触发确认回调",
                        tx.getTxHash(), confirmations, properties.getRequiredConfirmations());
                confirmationCallbacks.forEach(callback -> {
                    try {
                        callback.accept(tx);
                    } catch (Exception e) {
                        // 回调异常不影响区块推进与其它回调执行
                        log.error("[模拟链] 确认回调执行异常：{}", e.getMessage());
                    }
                });
            }
        });
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
