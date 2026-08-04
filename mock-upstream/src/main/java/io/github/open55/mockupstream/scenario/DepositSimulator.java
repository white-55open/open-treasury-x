package io.github.open55.mockupstream.scenario;

import io.github.open55.mockupstream.blockchain.BlockchainSimulator;
import io.github.open55.mockupstream.blockchain.SimulatedTx;
import io.github.open55.mockupstream.config.SimulatorProperties;
import io.github.open55.mockupstream.upstream.ApiResult;
import io.github.open55.mockupstream.upstream.OtxClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 充值故事编排器。
 * <p>
 * 完整演绎"链上充值事件 → 确认数增长 → 确认达标 → 通知 OTX 入账"：
 * 生成用户与业务号 → 在模拟链注册充值交易 → 注册确认达标回调（确认数达到
 * 阈值时自动调用 OTX POST /deposit）→ 等待回调完成 → 重复调用验证幂等
 * （同一 bizNo 不重复入账）→ 打印余额快照。
 */
@Slf4j
@Component
public class DepositSimulator {

    /**
     * 用户标识自增偏移量（配合时间戳保证每轮演示 uid 唯一，避免跨轮数据污染）
     */
    private static final AtomicLong UID_OFFSET = new AtomicLong();

    /**
     * 生成演示用户标识：100000 + 时间戳派生值，每轮演示唯一（幂等开户可重复）。
     *
     * @return 用户标识
     */
    private long nextUid() {
        return 100000L + (System.currentTimeMillis() % 900000L) + UID_OFFSET.incrementAndGet() % 1000L;
    }

    /**
     * 模拟热钱包地址（充值资金来源方）
     */
    private static final String HOT_WALLET_ADDRESS = "0x1111111111111111111111111111111111111111";

    /**
     * 模拟链 ID（Sepolia 测试网）
     */
    private static final String CHAIN_ID = "11155111";

    /**
     * 充值币种
     */
    private static final String CURRENCY = "USDT";

    /**
     * USDT 小数位（6 位），用于将展示金额换算为链上最小单位 wei
     */
    private static final int USDT_DECIMALS = 6;

    /**
     * 单笔充值演示金额
     */
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("100");

    /**
     * 模拟链（注册交易 + 确认达标回调）
     */
    private final BlockchainSimulator blockchainSimulator;

    /**
     * OTX REST 客户端
     */
    private final OtxClient otxClient;

    /**
     * 模拟器配置（确认阈值、区块间隔等）
     */
    private final SimulatorProperties properties;

    /**
     * 构造充值故事编排器。
     *
     * @param blockchainSimulator 模拟链
     * @param otxClient           OTX 客户端
     * @param properties          模拟器配置
     */
    public DepositSimulator(BlockchainSimulator blockchainSimulator, OtxClient otxClient,
                            SimulatorProperties properties) {
        this.blockchainSimulator = blockchainSimulator;
        this.otxClient = otxClient;
        this.properties = properties;
    }

    /**
     * 演绎充值故事线。
     * <p>
     * 步骤 1：创建用户账户（真实上游场景：用户注册时开户）；
     * 步骤 2：生成充值请求（uid/bizNo/金额）并注册模拟链上交易；
     * 步骤 3：等待链上确认数达标，由回调调用 POST /deposit 完成入账；
     * 步骤 4：同一 bizNo 重复调用 POST /deposit 验证幂等（不重复入账）。
     * 全程打印中文步骤日志与余额快照；任一环节失败仅统计失败步骤，不中断演示。
     *
     * @return 充值故事产出（uid + 执行结果统计）
     */
    public DepositOutcome runDepositStory() {
        StoryTracker tracker = new StoryTracker("充值故事");
        long uid = nextUid();
        String bizNo = "MOCK-DEP-" + System.currentTimeMillis();
        String toAddress = mockUserAddress(uid);
        BigInteger amountWei = DEPOSIT_AMOUNT.movePointRight(USDT_DECIMALS).toBigIntegerExact();

        log.info("==================== [充值故事] 开始 ====================");
        // 步骤 1：创建用户账户（真实上游场景：用户注册时开户，重复开户幂等）
        log.info("[充值故事] 步骤 1/4：创建用户账户（uid={}，真实场景中注册即开户）", uid);
        tracker.track(otxClient.createAccount(uid).isSuccess());

        // 步骤 2：生成充值请求并注册模拟链上交易
        log.info("[充值故事] 步骤 2/4：生成充值请求并注册模拟链上交易");
        log.info("[充值故事]   uid={}，bizNo={}，amount={} {}，chainId={}，充值地址={}",
                uid, bizNo, DEPOSIT_AMOUNT, CURRENCY, CHAIN_ID, toAddress);
        SimulatedTx tx = blockchainSimulator.registerTx(HOT_WALLET_ADDRESS, toAddress, amountWei);
        tracker.track(true);

        // 步骤 3：注册确认达标回调，等待模拟链确认数达到阈值后通知 OTX 入账
        CountDownLatch confirmedLatch = new CountDownLatch(1);
        AtomicBoolean depositCalled = new AtomicBoolean(false);
        AtomicReference<Boolean> depositStepResult = new AtomicReference<>();
        blockchainSimulator.onConfirmationsMet(simulatedTx -> {
            // 防重复：同一交易确认达标回调只执行一次入账
            if (!depositCalled.compareAndSet(false, true)) {
                return;
            }
            log.info("[充值故事] 步骤 3/4：链上确认达标（{} 确认 ≥ {}），通知 OTX 充值入账",
                    blockchainSimulator.confirmationsOf(simulatedTx.getTxHash()),
                    properties.getRequiredConfirmations());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("uid", uid);
            body.put("amount", DEPOSIT_AMOUNT);
            body.put("bizNo", bizNo);
            body.put("currency", CURRENCY);
            body.put("chainId", CHAIN_ID);
            body.put("chainTxHash", simulatedTx.getTxHash());
            depositStepResult.set(otxClient.deposit(body).isSuccess());
            confirmedLatch.countDown();
        });
        log.info("[充值故事] 步骤 3/4：等待模拟链确认数达到 {}（区块每 {}ms 推进一块）...",
                properties.getRequiredConfirmations(), properties.getBlockIntervalMs());
        // 等待确认回调；超时保护：确认阈值 × 区块间隔 + 15 秒缓冲
        long timeoutMs = (long) properties.getRequiredConfirmations() * properties.getBlockIntervalMs() + 15000;
        try {
            boolean callbackDone = confirmedLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            Boolean stepResult = depositStepResult.get();
            if (callbackDone && stepResult != null) {
                tracker.track(stepResult);
            } else {
                // 回调未在超时内完成（OTX 不可达或确认阈值过高），计为失败步骤
                log.warn("[充值故事] 等待确认回调超时（{}ms），请检查 OTX 是否可达", timeoutMs);
                tracker.track(false);
            }
        } catch (InterruptedException e) {
            // 主线程被中断：恢复中断标记并计为失败步骤
            Thread.currentThread().interrupt();
            tracker.track(false);
        }

        // 步骤 4：幂等验证——同一 bizNo 重复调用，OTX 应返回相同结果且不重复入账
        log.info("[充值故事] 步骤 4/4：幂等验证——同一 bizNo 重复调用 POST /deposit（OTX 应返回相同结果、不重复入账）");
        Map<String, Object> idempotentBody = new LinkedHashMap<>();
        idempotentBody.put("uid", uid);
        idempotentBody.put("amount", DEPOSIT_AMOUNT);
        idempotentBody.put("bizNo", bizNo);
        idempotentBody.put("currency", CURRENCY);
        idempotentBody.put("chainId", CHAIN_ID);
        idempotentBody.put("chainTxHash", tx.getTxHash());
        tracker.track(otxClient.deposit(idempotentBody).isSuccess());

        printAccountSnapshot("充值故事（入账后）", uid);
        log.info("==================== [充值故事] 结束（成功 {}/{} 步） ====================",
                tracker.result().successSteps(), tracker.result().totalSteps());
        return new DepositOutcome(uid, tracker.result());
    }

    /**
     * 打印账户余额快照（可用/冻结），展示 OTX 落账联动。
     *
     * @param scene 快照场景说明（如"充值入账后"）
     * @param uid   用户标识
     */
    private void printAccountSnapshot(String scene, Long uid) {
        ApiResult result = otxClient.getAccount(uid);
        if (result.data() != null && result.data().isObject()) {
            String available = result.data().path("availableBalance").asText("?");
            String frozen = result.data().path("frozenBalance").asText("?");
            log.info("[余额快照] {}：uid={} → 可用 {}，冻结 {}", scene, uid, available, frozen);
        } else {
            log.warn("[余额快照] {}：uid={} 查询失败（{}）", scene, uid,
                    result.message().isBlank() ? "OTX 不可达" : result.message());
        }
    }

    /**
     * 由 uid 派生确定性模拟用户地址（40 位 hex = 20 字节以太坊地址格式）。
     *
     * @param uid 用户标识
     * @return 模拟用户充值地址
     */
    private String mockUserAddress(long uid) {
        return String.format("0x%040x", uid);
    }
}
