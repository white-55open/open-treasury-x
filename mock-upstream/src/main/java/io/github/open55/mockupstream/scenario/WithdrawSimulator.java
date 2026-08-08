package io.github.open55.mockupstream.scenario;

import io.github.open55.mockupstream.blockchain.BlockchainSimulator;
import io.github.open55.mockupstream.config.SimulatorProperties;
import io.github.open55.mockupstream.state.OperationLogEntry;
import io.github.open55.mockupstream.state.RegistryStore;
import io.github.open55.mockupstream.state.WithdrawRecord;
import io.github.open55.mockupstream.upstream.ApiResult;
import io.github.open55.mockupstream.upstream.OtxClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提现业务编排器（用户操作与内部处理分离）。
 * <p>
 * 提供四个动作：
 * <ul>
 *   <li>{@link #applyWithdraw(Long, BigDecimal)}：用户操作——调用 OTX 冻结
 *       资金，生成已冻结提现记录（FROZEN）；</li>
 *   <li>{@link #markCancel(String)}：用户操作——标记取消提现（仅置
 *       CANCEL_PENDING，不直接调用 OTX，解冻由一键处理完成）；</li>
 *   <li>{@link #settleWithdraw(WithdrawRecord)}：内部处理——广播 → 模拟链上
 *       确认 → 确认结算（→ SETTLED）；</li>
 *   <li>{@link #cancelWithdraw(WithdrawRecord)}：内部处理——广播（留请求记录）
 *       → 取消解冻（→ CANCELLED）。</li>
 * </ul>
 * 每个动作均在操作日志记录业务身份、动作说明、HTTP 端点与 OTX 响应。
 */
@Slf4j
@Component
public class WithdrawSimulator {

    /**
     * 提现币种（与充值一致）
     */
    private static final String CURRENCY = "USDT";

    /**
     * 模拟链 ID（Sepolia 测试网）
     */
    private static final String CHAIN_ID = "11155111";

    /**
     * 用户提现目标假地址
     */
    private static final String TO_ADDRESS = "0x3333333333333333333333333333333333333333";

    /**
     * OTX REST 客户端
     */
    private final OtxClient otxClient;

    /**
     * 模拟链（确认数推进，模拟提现链上确认等待）
     */
    private final BlockchainSimulator blockchainSimulator;

    /**
     * 内存状态注册表（提现记录 + 操作日志）
     */
    private final RegistryStore store;

    /**
     * 模拟器配置（确认阈值等）
     */
    private final SimulatorProperties properties;

    /**
     * 构造提现业务编排器。
     *
     * @param otxClient           OTX 客户端
     * @param blockchainSimulator 模拟链
     * @param store               内存状态注册表
     * @param properties          模拟器配置
     */
    public WithdrawSimulator(OtxClient otxClient, BlockchainSimulator blockchainSimulator,
                             RegistryStore store, SimulatorProperties properties) {
        this.otxClient = otxClient;
        this.blockchainSimulator = blockchainSimulator;
        this.store = store;
        this.properties = properties;
    }

    /**
     * 申请提现（用户操作）：调用 OTX 冻结资金并生成提现记录。
     * <p>
     * 真实场景映射：用户提交提现申请，平台立即冻结申请金额防止重复消费。
     * 冻结成功后记录置为 FROZEN，等待一键处理广播与结算。
     *
     * @param uid    提现用户标识
     * @param amount 提现金额（展示单位）
     * @return 新建的提现记录（状态 FROZEN）
     */
    public WithdrawRecord applyWithdraw(Long uid, BigDecimal amount) {
        String freezeBizNo = "MOCK-WD-FREEZE-" + System.currentTimeMillis();
        String requestBizNo = "MOCK-WD-" + System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uid", uid);
        body.put("bizNo", freezeBizNo);
        body.put("amount", amount);
        body.put("currency", CURRENCY);
        ApiResult result = otxClient.freeze(body);
        WithdrawRecord record = store.registerWithdraw(requestBizNo, freezeBizNo, uid, amount, CURRENCY, TO_ADDRESS);
        store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                "👤 用户 #" + uid + " 提现",
                "提交提现申请 " + amount + " " + CURRENCY + "，平台冻结资金",
                "POST /withdraw/freeze", summarize(result)));
        log.info("[Withdraw] applied uid={}, freezeBizNo={}, requestBizNo={}, amount={} {}",
                uid, freezeBizNo, requestBizNo, amount, CURRENCY);
        return record;
    }

    /**
     * 标记取消提现（用户操作）：仅置记录为取消处理中，不直接调用 OTX。
     * <p>
     * 真实场景映射：用户主动取消提现申请，解冻动作由一键处理统一完成。
     * 已结算（SETTLED）的提现不可取消。
     *
     * @param bizNo 提现请求业务号
     * @return true 表示标记成功；false 表示记录不存在或已结算
     */
    public boolean markCancel(String bizNo) {
        WithdrawRecord record = store.findWithdraw(bizNo);
        if (record == null) {
            store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                    "👤 用户操作", "取消提现失败：单据不存在（" + bizNo + "）", "-", "-"));
            return false;
        }
        if (record.getStatus() == WithdrawRecord.Status.SETTLED) {
            store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                    "👤 用户 #" + record.getUid() + " 提现",
                    "取消提现被拒绝：单据已结算不可取消（" + bizNo + "）", "-", "-"));
            return false;
        }
        record.setStatus(WithdrawRecord.Status.CANCEL_PENDING);
        store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                "👤 用户 #" + record.getUid() + " 提现",
                "取消提现申请 " + record.getAmount() + " " + record.getCurrency() + "，等待解冻",
                "-", "-"));
        return true;
    }

    /**
     * 确认结算（内部处理）：广播 → 模拟链上确认 → 确认结算。
     * <p>
     * 真实场景映射：平台审核通过后广播提现交易上链，链上确认达标后
     * 完成冻结资金扣减与凭证过账。广播失败（如 dev 环境 keystore 缺失）
     * 仅记录日志不阻断，结算结果由 OTX 返回决定。
     *
     * @param record 提现记录（FROZEN）
     * @return true 表示结算成功且记录置为 SETTLED；false 表示失败（保持 FROZEN）
     */
    public boolean settleWithdraw(WithdrawRecord record) {
        // 步骤一：广播提现交易（结果仅记录日志，不阻断后续结算）
        Map<String, Object> broadcastBody = new LinkedHashMap<>();
        broadcastBody.put("uid", record.getUid());
        broadcastBody.put("bizNo", record.getBizNo());
        broadcastBody.put("amount", record.getAmount());
        broadcastBody.put("currency", record.getCurrency());
        broadcastBody.put("chainId", CHAIN_ID);
        broadcastBody.put("toAddress", record.getToAddress());
        ApiResult broadcastResult = otxClient.broadcast(broadcastBody);
        store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                "⚙ 内部处理", "广播提现交易上链",
                "POST /withdraw/broadcast", summarize(broadcastResult)));

        // 步骤二：模拟链上确认等待（推进确认数展示等待过程）
        blockchainSimulator.advanceTo(blockchainSimulator.currentHeight() + properties.getRequiredConfirmations());
        store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                "⚙ 内部处理", "等待链上确认达标（模拟）", "-", "-"));

        // 步骤三：确认结算（OTX 侧完成扣款与凭证过账）
        ApiResult settleResult = otxClient.confirmSettle(record.getBizNo());
        store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                "⚙ 内部处理", "链上确认达标，确认结算扣款",
                "POST /withdraw/" + record.getBizNo() + "/confirm-settle", summarize(settleResult)));
        if (settleResult.isSuccess()) {
            record.setStatus(WithdrawRecord.Status.SETTLED);
            return true;
        }
        return false;
    }

    /**
     * 取消解冻（内部处理）：广播（留请求记录）→ 取消解冻。
     * <p>
     * 真实场景映射：风控拒绝或用户取消后，平台将已冻结资金释放回可用余额。
     * 广播失败（dev 环境）仍可取消——取消端点在请求记录上操作。
     *
     * @param record 提现记录（CANCEL_PENDING）
     * @return true 表示取消成功且记录置为 CANCELLED；false 表示失败（保持 CANCEL_PENDING）
     */
    public boolean cancelWithdraw(WithdrawRecord record) {
        Map<String, Object> broadcastBody = new LinkedHashMap<>();
        broadcastBody.put("uid", record.getUid());
        broadcastBody.put("bizNo", record.getBizNo());
        broadcastBody.put("amount", record.getAmount());
        broadcastBody.put("currency", record.getCurrency());
        broadcastBody.put("chainId", CHAIN_ID);
        broadcastBody.put("toAddress", record.getToAddress());
        ApiResult broadcastResult = otxClient.broadcast(broadcastBody);
        store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                "⚙ 内部处理", "提交广播创建提现请求记录",
                "POST /withdraw/broadcast", summarize(broadcastResult)));

        ApiResult cancelResult = otxClient.cancel(record.getBizNo());
        store.appendLog(new OperationLogEntry(System.currentTimeMillis(),
                "⚙ 内部处理", "取消提现，解冻资金回可用余额",
                "POST /withdraw/" + record.getBizNo() + "/cancel", summarize(cancelResult)));
        if (cancelResult.isSuccess()) {
            record.setStatus(WithdrawRecord.Status.CANCELLED);
            return true;
        }
        return false;
    }

    /**
     * 生成 OTX 响应摘要（业务码 + 消息，供操作日志展示）。
     *
     * @param result OTX 调用结果
     * @return 摘要文本（如 "200 处理成功"）
     */
    private String summarize(ApiResult result) {
        if (result.isSuccess()) {
            return "成功（" + result.code() + " " + result.message() + "）";
        }
        return "失败（" + (result.message().isBlank() ? "OTX 不可达" : result.message()) + "）";
    }
}
