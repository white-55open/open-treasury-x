package io.github.open55.mockupstream.scenario;

import io.github.open55.mockupstream.blockchain.BlockchainSimulator;
import io.github.open55.mockupstream.blockchain.SimulatedTx;
import io.github.open55.mockupstream.config.SimulatorProperties;
import io.github.open55.mockupstream.state.DepositRecord;
import io.github.open55.mockupstream.state.OperationLogEntry;
import io.github.open55.mockupstream.state.RegistryStore;
import io.github.open55.mockupstream.upstream.ApiResult;
import io.github.open55.mockupstream.upstream.OtxClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 充值业务编排器（用户操作与内部处理分离）。
 * <p>
 * 提供两个动作：
 * <ul>
 *   <li>{@link #initiateDeposit(Long, BigDecimal)}：用户操作——幂等开户并在
 *       模拟链注册充值交易，生成确认中的充值记录（CONFIRMING）；</li>
 *   <li>{@link #bookDeposit(DepositRecord)}：内部处理——推进确认数至达标后
 *       调用 OTX 入账（POST /deposit），成功置为已入账（BOOKED），失败保持
 *       CONFIRMING 供下次一键处理重试。</li>
 * </ul>
 * 每个动作均在操作日志记录业务身份、动作说明、HTTP 端点与 OTX 响应。
 */
@Slf4j
@Component
public class DepositSimulator {

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
     * 模拟链（注册交易 + 确认数推进）
     */
    private final BlockchainSimulator blockchainSimulator;

    /**
     * OTX REST 客户端
     */
    private final OtxClient otxClient;

    /**
     * 内存状态注册表（充值记录 + 操作日志）
     */
    private final RegistryStore store;

    /**
     * 模拟器配置（确认阈值等）
     */
    private final SimulatorProperties properties;

    /**
     * 构造充值业务编排器。
     *
     * @param blockchainSimulator 模拟链
     * @param otxClient           OTX 客户端
     * @param store               内存状态注册表
     * @param properties          模拟器配置
     */
    public DepositSimulator(BlockchainSimulator blockchainSimulator, OtxClient otxClient,
                            RegistryStore store, SimulatorProperties properties) {
        this.blockchainSimulator = blockchainSimulator;
        this.otxClient = otxClient;
        this.store = store;
        this.properties = properties;
    }

    /**
     * 发起充值（用户操作）：幂等开户 → 注册模拟链交易 → 生成确认中记录。
     * <p>
     * 真实场景映射：用户向平台充值地址转账，链上打包即确认数 1/12，
     * 尚未达标，等待「一键处理」推进确认后入账。
     *
     * @param uid    充值用户标识
     * @param amount 充值金额（展示单位）
     * @return 新建的充值记录（状态 CONFIRMING）
     */
    public DepositRecord initiateDeposit(Long uid, BigDecimal amount) {
        // 真实上游场景：用户注册时已开户（幂等开户不重复创建）
        otxClient.createAccount(uid);
        BigInteger amountWei = amount.movePointRight(USDT_DECIMALS).toBigIntegerExact();
        SimulatedTx tx = blockchainSimulator.registerTx(HOT_WALLET_ADDRESS, mockUserAddress(uid), amountWei);
        String bizNo = "MOCK-DEP-" + System.currentTimeMillis();
        DepositRecord record = store.registerDeposit(bizNo, uid, amount, CURRENCY, tx.getTxHash());
        store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                "👤 用户 #" + uid + " 充值",
                "向平台充值地址转账 " + amount + " " + CURRENCY + "，模拟链打包，等待确认",
                "-", "-"));
        log.info("[Deposit] initiated uid={}, bizNo={}, amount={} {}, tx={}", uid, bizNo, amount, CURRENCY, tx.getTxHash());
        return record;
    }

    /**
     * 确认入账（内部处理）：推进确认数至达标后调用 OTX 入账。
     * <p>
     * 真实场景映射：链上确认达标，业务系统通知 OTX 入账。确认数已达标时
     * 推进为空操作，直接重试入账（支持一键处理失败后的重试语义）。
     *
     * @param record 充值记录（CONFIRMING）
     * @return true 表示入账成功且记录置为 BOOKED；false 表示失败（保持 CONFIRMING）
     */
    public boolean bookDeposit(DepositRecord record) {
        long confirmations = blockchainSimulator.confirmationsOf(record.getTxHash());
        long targetHeight = blockchainSimulator.currentHeight()
                + (properties.getRequiredConfirmations() - confirmations);
        blockchainSimulator.advanceTo(targetHeight);
        long after = blockchainSimulator.confirmationsOf(record.getTxHash());
        log.info("[Deposit] confirmations reached {}/{} for tx {}, calling OTX deposit",
                after, properties.getRequiredConfirmations(), record.getTxHash());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uid", record.getUid());
        body.put("amount", record.getAmount());
        body.put("bizNo", record.getBizNo());
        body.put("currency", record.getCurrency());
        body.put("chainId", CHAIN_ID);
        body.put("chainTxHash", record.getTxHash());
        ApiResult result = otxClient.deposit(body);
        store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                "⚙ 内部处理",
                "确认达标（" + after + "/" + properties.getRequiredConfirmations() + "），通知 OTX 充值入账",
                "POST /deposit", summarize(result)));
        if (result.isSuccess()) {
            record.setStatus(DepositRecord.Status.BOOKED);
            return true;
        }
        return false;
    }

    /**
     * 生成 OTX 响应摘要（业务码 + 消息，供操作日志展示）。
     *
     * @param result OTX 调用结果
     * @return 摘要文本（如 "200 入账成功"）
     */
    private String summarize(ApiResult result) {
        if (result.isSuccess()) {
            return "成功（" + result.code() + " " + result.message() + "）";
        }
        return "失败（" + (result.message().isBlank() ? "OTX 不可达" : result.message()) + "）";
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
