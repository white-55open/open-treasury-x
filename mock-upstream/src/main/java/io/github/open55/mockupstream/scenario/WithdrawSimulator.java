package io.github.open55.mockupstream.scenario;

import io.github.open55.mockupstream.config.SimulatorProperties;
import io.github.open55.mockupstream.upstream.ApiResult;
import io.github.open55.mockupstream.upstream.OtxClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 提现故事编排器。
 * <p>
 * 提供两条提现故事线：
 * <ul>
 *   <li>结算线（runSettlementStory）：提现冻结 → 链上广播 → 模拟确认达标 → 确认结算，
 *       完整展示提现从申请到扣款的账务闭环；</li>
 *   <li>取消线（runCancellationStory）：提现冻结 → 直接取消（解冻回可用余额），
 *       展示风控拒绝/用户取消场景。</li>
 * </ul>
 * 每步调用 OTX 后打印余额快照（可用/冻结）展示落账联动；单步失败（含 dev 环境
 * 广播失败路径）仅统计失败并继续后续步骤，不中断演示。
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
     * 结算故事线演示提现金额
     */
    private static final BigDecimal SETTLE_AMOUNT = new BigDecimal("50");

    /**
     * 取消故事线演示提现金额
     */
    private static final BigDecimal CANCEL_AMOUNT = new BigDecimal("50");

    /**
     * OTX REST 客户端
     */
    private final OtxClient otxClient;

    /**
     * 模拟器配置（步骤延时等）
     */
    private final SimulatorProperties properties;

    /**
     * 构造提现故事编排器。
     *
     * @param otxClient  OTX 客户端
     * @param properties 模拟器配置
     */
    public WithdrawSimulator(OtxClient otxClient, SimulatorProperties properties) {
        this.otxClient = otxClient;
        this.properties = properties;
    }

    /**
     * 演绎提现结算故事线：冻结 → 广播 → 模拟链上确认 → 确认结算。
     * <p>
     * 按 OTX 契约：链下账务（冻结）与链上请求（广播/结算）使用**不同业务号**——
     * fund_flow_t.uk_biz_no 唯一索引限定同一 bizNo 只能有一条资金流水，
     * 冻结的 FREEZE 流水与结算的 WITHDRAW 流水必须分号。
     * 步骤间按配置延时（step-delay-ms），便于配合管理控制台逐步观察。
     * dev 环境下广播若因 keystore/RPC 缺失而失败，OTX 会返回 FAILED 状态，
     * 故事线打印实际结果并继续（演示"广播失败路径"本身也是真实集成行为）。
     *
     * @param uid 提现用户标识（应为充值故事的用户，保证可用余额充足）
     * @return 故事线执行结果统计
     */
    public StoryResult runSettlementStory(Long uid) {
        StoryTracker tracker = new StoryTracker("提现结算故事");
        String freezeBizNo = "MOCK-WD-SETTLE-FREEZE-" + System.currentTimeMillis();
        String requestBizNo = "MOCK-WD-SETTLE-" + System.currentTimeMillis();
        log.info("==================== [提现结算故事] 开始 ====================");

        // 步骤 1/4：提现冻结（链下账务，独立业务号）
        log.info("[提现结算故事] 步骤 1/4：发起提现申请并冻结资金（uid={}，冻结号={}，请求号={}，amount={} {}）",
                uid, freezeBizNo, requestBizNo, SETTLE_AMOUNT, CURRENCY);
        tracker.track(otxClient.freeze(Map.of(
                "uid", uid, "bizNo", freezeBizNo, "amount", SETTLE_AMOUNT, "currency", CURRENCY)).isSuccess());
        printAccountSnapshot("提现冻结后", uid);
        sleep(properties.getStepDelayMs());

        // 步骤 2/4：提现广播（链上请求号；dev 环境可能失败，仅展示结果）
        log.info("[提现结算故事] 步骤 2/4：向链上广播提现交易（请求号={}，chainId={}，toAddress={}）",
                requestBizNo, CHAIN_ID, TO_ADDRESS);
        tracker.track(otxClient.broadcast(Map.of(
                "uid", uid, "bizNo", requestBizNo, "amount", SETTLE_AMOUNT, "currency", CURRENCY,
                "chainId", CHAIN_ID, "toAddress", TO_ADDRESS)).isSuccess());
        printAccountSnapshot("提现广播后", uid);
        sleep(properties.getStepDelayMs());

        // 步骤 3/4：模拟等待链上确认达标（演示简化：延时代替实际确认轮询）
        log.info("[提现结算故事] 步骤 3/4：模拟等待链上确认达标（演示简化：延时 {}ms 代替实际确认轮询）",
                properties.getStepDelayMs());
        sleep(properties.getStepDelayMs());
        tracker.track(true);

        // 步骤 4/4：确认结算（链上确认达标后完成冻结资金扣减与凭证过账）
        log.info("[提现结算故事] 步骤 4/4：链上确认达标，调 OTX 完成结算扣款（请求号={}）", requestBizNo);
        tracker.track(otxClient.confirmSettle(requestBizNo).isSuccess());
        printAccountSnapshot("提现结算后", uid);

        log.info("==================== [提现结算故事] 结束（成功 {}/{} 步） ====================",
                tracker.result().successSteps(), tracker.result().totalSteps());
        return tracker.result();
    }

    /**
     * 演绎提现取消故事线：冻结 → 提交广播（留请求记录）→ 取消（解冻回可用余额）。
     * <p>
     * 展示风控拒绝/用户主动取消场景。按 OTX 契约：链下冻结与链上请求用不同
     * 业务号（fund_flow_t.uk_biz_no 唯一索引）；取消端点在提现请求记录上操作
     * （请求在广播时创建，广播失败也会留下 FAILED 记录），故取消前先提交
     * 广播以创建请求记录；取消后冻结资金全部释放回可用余额。
     *
     * @param uid 提现用户标识
     * @return 故事线执行结果统计
     */
    public StoryResult runCancellationStory(Long uid) {
        StoryTracker tracker = new StoryTracker("提现取消故事");
        String freezeBizNo = "MOCK-WD-CANCEL-FREEZE-" + System.currentTimeMillis();
        String requestBizNo = "MOCK-WD-CANCEL-" + System.currentTimeMillis();
        log.info("==================== [提现取消故事] 开始 ====================");

        // 步骤 1/3：提现冻结（链下账务，独立业务号）
        log.info("[提现取消故事] 步骤 1/3：发起提现申请并冻结资金（uid={}，冻结号={}，请求号={}，amount={} {}）",
                uid, freezeBizNo, requestBizNo, CANCEL_AMOUNT, CURRENCY);
        tracker.track(otxClient.freeze(Map.of(
                "uid", uid, "bizNo", freezeBizNo, "amount", CANCEL_AMOUNT, "currency", CURRENCY)).isSuccess());
        printAccountSnapshot("提现冻结后", uid);
        sleep(properties.getStepDelayMs());

        // 步骤 2/3：提交广播创建提现请求记录（dev 环境广播失败仅留 FAILED 记录，取消仍可进行）
        log.info("[提现取消故事] 步骤 2/3：提交广播创建提现请求记录（请求号={}，取消端点在请求记录上操作）", requestBizNo);
        tracker.track(otxClient.broadcast(Map.of(
                "uid", uid, "bizNo", requestBizNo, "amount", CANCEL_AMOUNT, "currency", CURRENCY,
                "chainId", CHAIN_ID, "toAddress", TO_ADDRESS)).isSuccess());
        sleep(properties.getStepDelayMs());

        // 步骤 3/3：取消提现（解冻资金回可用余额，请求置 CANCELLED）
        log.info("[提现取消故事] 步骤 3/3：取消提现（风控拒绝/用户取消），解冻资金回可用余额（请求号={}）", requestBizNo);
        tracker.track(otxClient.cancel(requestBizNo).isSuccess());
        printAccountSnapshot("提现取消后", uid);

        log.info("==================== [提现取消故事] 结束（成功 {}/{} 步） ====================",
                tracker.result().successSteps(), tracker.result().totalSteps());
        return tracker.result();
    }

    /**
     * 打印账户余额快照（可用/冻结），展示 OTX 落账联动。
     *
     * @param scene 快照场景说明（如"提现冻结后"）
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
     * 步骤间延时（中断时恢复中断标记）。
     *
     * @param millis 延时毫秒数
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
