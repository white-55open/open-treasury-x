package io.github.open55.mockupstream.scenario;

import io.github.open55.mockupstream.state.DepositRecord;
import io.github.open55.mockupstream.state.RegistryStore;
import io.github.open55.mockupstream.state.WithdrawRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 一键处理编排器：唯一的内部触发入口。
 * <p>
 * 批量推进所有挂起事项（按序）：
 * <ol>
 *   <li>全部"确认中"充值（CONFIRMING）→ 推进确认达标 → POST /deposit 入账；</li>
 *   <li>全部"已冻结"提现（FROZEN）→ 广播 → 确认 → 结算；</li>
 *   <li>全部"取消处理中"提现（CANCEL_PENDING）→ 取消解冻。</li>
 * </ol>
 * 单笔失败（如 OTX 不可达）不影响其余事项；失败项保持原状态，下次一键处理可重试。
 */
@Slf4j
@Component
public class ProcessOrchestrator {

    /**
     * 内存状态注册表（挂起事项数据源）
     */
    private final RegistryStore store;

    /**
     * 充值业务编排器（确认入账）
     */
    private final DepositSimulator depositSimulator;

    /**
     * 提现业务编排器（结算/取消）
     */
    private final WithdrawSimulator withdrawSimulator;

    /**
     * 构造一键处理编排器。
     *
     * @param store              内存状态注册表
     * @param depositSimulator   充值业务编排器
     * @param withdrawSimulator  提现业务编排器
     */
    public ProcessOrchestrator(RegistryStore store, DepositSimulator depositSimulator,
                               WithdrawSimulator withdrawSimulator) {
        this.store = store;
        this.depositSimulator = depositSimulator;
        this.withdrawSimulator = withdrawSimulator;
    }

    /**
     * 执行一键处理：依序推进全部挂起事项并统计结果。
     * <p>
     * 单笔失败仅计数并保持状态，不中断整体流程；无挂起事项时零动作。
     *
     * @return 处理结果统计
     */
    public ProcessSummary process() {
        // 阶段一：全部确认中充值推进入账
        int depositBooked = 0;
        int depositFailed = 0;
        for (DepositRecord record : store.listDeposits()) {
            // 仅处理确认中记录（已入账跳过）
            if (record.getStatus() == DepositRecord.Status.CONFIRMING) {
                if (depositSimulator.bookDeposit(record)) {
                    depositBooked++;
                } else {
                    depositFailed++;
                }
            }
        }

        // 阶段二/三：提现结算与取消解冻（按记录列表顺序）
        int settled = 0;
        int settleFailed = 0;
        int cancelled = 0;
        int cancelFailed = 0;
        for (WithdrawRecord record : store.listWithdraws()) {
            if (record.getStatus() == WithdrawRecord.Status.FROZEN) {
                // 已冻结提现推进结算
                if (withdrawSimulator.settleWithdraw(record)) {
                    settled++;
                } else {
                    settleFailed++;
                }
            } else if (record.getStatus() == WithdrawRecord.Status.CANCEL_PENDING) {
                // 取消处理中提现完成解冻
                if (withdrawSimulator.cancelWithdraw(record)) {
                    cancelled++;
                } else {
                    cancelFailed++;
                }
            }
        }

        log.info("[Process] done: deposit booked={}, failed={}; settled={}, failed={}; cancelled={}, failed={}",
                depositBooked, depositFailed, settled, settleFailed, cancelled, cancelFailed);
        return new ProcessSummary(depositBooked, depositFailed, settled, settleFailed, cancelled, cancelFailed);
    }
}
