package io.github.open55.mockupstream.scenario;

import io.github.open55.mockupstream.blockchain.BlockchainSimulator;
import io.github.open55.mockupstream.config.SimulatorProperties;
import io.github.open55.mockupstream.state.RegistryStore;
import io.github.open55.mockupstream.state.WithdrawRecord;
import io.github.open55.mockupstream.upstream.ApiResult;
import io.github.open55.mockupstream.upstream.OtxClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提现业务编排器单测。
 * <p>
 * 覆盖范围：申请提现调冻结并生成 FROZEN 记录、取消标记置 CANCEL_PENDING、
 * 已结算提现不可取消、结算/取消解冻成功置终态、操作日志记录端点。
 */
class WithdrawSimulatorTest {

    /**
     * 被测对象
     */
    private WithdrawSimulator withdrawSimulator;

    /**
     * OTX 客户端 mock
     */
    private OtxClient otxClient;

    /**
     * 模拟链 mock
     */
    private BlockchainSimulator blockchainSimulator;

    /**
     * 真实内存注册表
     */
    private RegistryStore store;

    /**
     * 每次测试前重建被测对象与依赖。
     */
    @BeforeEach
    void setUp() {
        otxClient = mock(OtxClient.class);
        blockchainSimulator = mock(BlockchainSimulator.class);
        store = new RegistryStore();
        SimulatorProperties properties = new SimulatorProperties();
        properties.setRequiredConfirmations(12);
        withdrawSimulator = new WithdrawSimulator(otxClient, blockchainSimulator, store, properties);
    }

    /**
     * 申请提现：调用冻结端点并生成 FROZEN 记录（冻结号与请求号分离）。
     */
    @Test
    void applyWithdraw_callsFreezeAndCreatesFrozenRecord() {
        when(otxClient.freeze(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));

        WithdrawRecord record = withdrawSimulator.applyWithdraw(100123L, new BigDecimal("50"));

        assertThat(record.getStatus()).isEqualTo(WithdrawRecord.Status.FROZEN);
        assertThat(record.getFreezeBizNo()).isNotEqualTo(record.getBizNo());
        verify(otxClient).freeze(any());
    }

    /**
     * 标记取消：记录置 CANCEL_PENDING，且不直接调用 OTX（解冻由一键处理完成）。
     */
    @Test
    void markCancel_setsCancelPendingWithoutOtxCall() {
        WithdrawRecord record = store.registerWithdraw("MOCK-WD-1", "MOCK-WD-1-FREEZE",
                100123L, new BigDecimal("50"), "USDT", "0x3333");

        boolean marked = withdrawSimulator.markCancel("MOCK-WD-1");

        assertThat(marked).isTrue();
        assertThat(record.getStatus()).isEqualTo(WithdrawRecord.Status.CANCEL_PENDING);
        verify(otxClient, never()).cancel(anyString());
    }

    /**
     * 已结算提现标记取消被拒绝：状态不变且不产生 OTX 调用。
     */
    @Test
    void markCancel_onSettledRecord_rejects() {
        WithdrawRecord record = store.registerWithdraw("MOCK-WD-2", "MOCK-WD-2-FREEZE",
                100123L, new BigDecimal("50"), "USDT", "0x3333");
        record.setStatus(WithdrawRecord.Status.SETTLED);

        boolean marked = withdrawSimulator.markCancel("MOCK-WD-2");

        assertThat(marked).isFalse();
        assertThat(record.getStatus()).isEqualTo(WithdrawRecord.Status.SETTLED);
        verify(otxClient, never()).cancel(anyString());
    }

    /**
     * 结算成功：依次广播、推进确认、确认结算，记录置 SETTLED。
     */
    @Test
    void settleWithdraw_success_marksSettled() {
        WithdrawRecord record = store.registerWithdraw("MOCK-WD-3", "MOCK-WD-3-FREEZE",
                100123L, new BigDecimal("50"), "USDT", "0x3333");
        when(blockchainSimulator.currentHeight()).thenReturn(10L);
        when(otxClient.broadcast(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));
        when(otxClient.confirmSettle("MOCK-WD-3")).thenReturn(new ApiResult(200, "200", "success", null, "{}"));

        boolean settled = withdrawSimulator.settleWithdraw(record);

        assertThat(settled).isTrue();
        assertThat(record.getStatus()).isEqualTo(WithdrawRecord.Status.SETTLED);
        verify(otxClient).broadcast(any());
        verify(otxClient).confirmSettle("MOCK-WD-3");
    }

    /**
     * 结算失败：记录保持 FROZEN（下次一键处理可重试）。
     */
    @Test
    void settleWithdraw_failure_keepsFrozen() {
        WithdrawRecord record = store.registerWithdraw("MOCK-WD-4", "MOCK-WD-4-FREEZE",
                100123L, new BigDecimal("50"), "USDT", "0x3333");
        when(blockchainSimulator.currentHeight()).thenReturn(10L);
        when(otxClient.broadcast(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));
        when(otxClient.confirmSettle("MOCK-WD-4")).thenReturn(new ApiResult(200, "TX_NOT_CONFIRMED", "not confirmed", null, "{}"));

        boolean settled = withdrawSimulator.settleWithdraw(record);

        assertThat(settled).isFalse();
        assertThat(record.getStatus()).isEqualTo(WithdrawRecord.Status.FROZEN);
    }

    /**
     * 取消解冻成功：广播留请求记录后取消，记录置 CANCELLED。
     */
    @Test
    void cancelWithdraw_success_marksCancelled() {
        WithdrawRecord record = store.registerWithdraw("MOCK-WD-5", "MOCK-WD-5-FREEZE",
                100123L, new BigDecimal("50"), "USDT", "0x3333");
        record.setStatus(WithdrawRecord.Status.CANCEL_PENDING);
        when(otxClient.broadcast(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));
        when(otxClient.cancel("MOCK-WD-5")).thenReturn(new ApiResult(200, "200", "success", null, "{}"));

        boolean cancelled = withdrawSimulator.cancelWithdraw(record);

        assertThat(cancelled).isTrue();
        assertThat(record.getStatus()).isEqualTo(WithdrawRecord.Status.CANCELLED);
        verify(otxClient).broadcast(any());
        verify(otxClient).cancel("MOCK-WD-5");
    }
}
