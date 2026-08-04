package io.github.open55.mockupstream.scenario;

import io.github.open55.mockupstream.config.SimulatorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示驱动：启动后自动顺序演绎完整业务故事线。
 * <p>
 * 顺序：充值故事（链上确认达标 → POST /deposit 入账）→ 提现结算故事
 * （冻结 → 广播 → 确认结算）→ 提现取消故事（冻结 → 取消解冻）。
 * 步骤间按配置延时，便于配合管理控制台逐步观察 OTX 落账效果。
 * <p>
 * 容错：OTX 未启动时各故事线内部逐步捕获异常并统计失败步骤，演示驱动
 * 自身不崩溃，结束时输出各故事线成功/失败步骤总结。
 */
@Slf4j
@Component
public class DemoRunner implements ApplicationRunner {

    /**
     * 充值故事编排器
     */
    private final DepositSimulator depositSimulator;

    /**
     * 提现故事编排器
     */
    private final WithdrawSimulator withdrawSimulator;

    /**
     * 模拟器配置（步骤延时等）
     */
    private final SimulatorProperties properties;

    /**
     * 构造演示驱动。
     *
     * @param depositSimulator   充值故事编排器
     * @param withdrawSimulator  提现故事编排器
     * @param properties         模拟器配置
     */
    public DemoRunner(DepositSimulator depositSimulator, WithdrawSimulator withdrawSimulator,
                      SimulatorProperties properties) {
        this.depositSimulator = depositSimulator;
        this.withdrawSimulator = withdrawSimulator;
        this.properties = properties;
    }

    /**
     * 应用启动后自动执行：按顺序演绎三条故事线并输出总结。
     * <p>
     * 充值故事产出用户标识（uid），提现两条故事线复用同一用户，
     * 保证余额上下文连贯（充值 100 → 结算扣 50 → 取消回 50，最终可用 50）。
     *
     * @param args 应用启动参数（本演示未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("================================================================");
        log.info("  模拟上游（mock-upstream）自动演绎启动");
        log.info("  目标 OTX：{}（请确认 OTX 已启动，否则失败步骤将被统计）", properties.getBaseUrl());
        log.info("  验证入口：http://localhost:8003/admin（管理控制台，查看落账明细）");
        log.info("================================================================");

        List<StoryResult> results = new ArrayList<>();

        // 1. 充值故事：链上确认达标 → POST /deposit 入账（等待确认需约 24 秒）
        DepositOutcome deposit = null;
        try {
            deposit = depositSimulator.runDepositStory();
            results.add(deposit.result());
        } catch (Exception e) {
            log.error("[充值故事] 整体执行异常：{}", e.getMessage());
            results.add(new StoryResult("充值故事", 0, 0));
        }
        sleep(properties.getStepDelayMs());

        // 2. 提现结算故事：冻结 → 广播 → 模拟确认 → 确认结算
        Long uid = deposit == null ? null : deposit.uid();
        if (uid != null) {
            try {
                results.add(withdrawSimulator.runSettlementStory(uid));
            } catch (Exception e) {
                log.error("[提现结算故事] 整体执行异常：{}", e.getMessage());
                results.add(new StoryResult("提现结算故事", 0, 0));
            }
            sleep(properties.getStepDelayMs());

            // 3. 提现取消故事：冻结 → 取消解冻
            try {
                results.add(withdrawSimulator.runCancellationStory(uid));
            } catch (Exception e) {
                log.error("[提现取消故事] 整体执行异常：{}", e.getMessage());
                results.add(new StoryResult("提现取消故事", 0, 0));
            }
        } else {
            log.warn("[演示驱动] 充值故事未产出用户（uid 为空），跳过提现故事线");
        }

        printSummary(results);
    }

    /**
     * 输出演示总结：各故事线成功/失败步骤统计与落账查看提示。
     *
     * @param results 各故事线执行结果
     */
    private void printSummary(List<StoryResult> results) {
        log.info("================================================================");
        log.info("  演示演绎结束——各故事线执行统计");
        log.info("================================================================");
        int totalSuccess = 0;
        int totalSteps = 0;
        for (StoryResult result : results) {
            log.info("  [{}] 成功 {}/{} 步（失败 {} 步）",
                    result.storyName(), result.successSteps(), result.totalSteps(), result.failedSteps());
            totalSuccess += result.successSteps();
            totalSteps += result.totalSteps();
        }
        if (totalSteps > 0 && totalSuccess == totalSteps) {
            log.info("  全部 {} 步演绎成功——业务全流程已完整落账", totalSteps);
        } else {
            log.info("  共 {} 步，成功 {} 步（失败 {} 步）——失败步骤多为 OTX 不可达或 dev 广播环境缺失，属预期展示路径",
                    totalSteps, totalSuccess, totalSteps - totalSuccess);
        }
        log.info("  请打开 http://localhost:8003/admin 管理控制台查看账户余额、资金流水与总账凭证");
        log.info("================================================================");
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
