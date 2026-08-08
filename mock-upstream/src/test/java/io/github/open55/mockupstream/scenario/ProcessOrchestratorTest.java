package io.github.open55.mockupstream.scenario;

import io.github.open55.mockupstream.blockchain.BlockchainSimulator;
import io.github.open55.mockupstream.config.SimulatorProperties;
import io.github.open55.mockupstream.state.RegistryStore;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一键处理编排器单测。
 * <p>
 * 覆盖范围：混合挂起（多笔充值 + 提现 + 取消）一次推进全部完成、
 * 单笔失败不影响其余、无挂起时零 OTX 调用。
 */
class ProcessOrchestratorTest {

    /**
     * 被测对象
     */
    private ProcessOrchestrator orchestrator;

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
        DepositSimulator depositSimulator = new DepositSimulator(blockchainSimulator, otxClient, store, properties);
        WithdrawSimulator withdrawSimulator = new WithdrawSimulator(otxClient, blockchainSimulator, store, properties);
        orchestrator = new ProcessOrchestrator(store, depositSimulator, withdrawSimulator);
    }

    /**
     * 混合挂起：两笔充值 + 一笔冻结提现 + 一笔取消提现，一次处理全部推进。
     */
    @Test
    void process_withMixedPending_advancesAll() {
        // 两笔确认中充值
        store.registerDeposit("MOCK-DEP-1", 100123L, new BigDecimal("100"), "USDT", "0xtx1");
        store.registerDeposit("MOCK-DEP-2", 100123L, new BigDecimal("200"), "USDT", "0xtx2");
        // 一笔冻结提现 + 一笔取消处理中提现
        store.registerWithdraw("MOCK-WD-1", "MOCK-WD-1-FREEZE", 100123L, new BigDecimal("50"), "USDT", "0x3333");
        store.registerWithdraw("MOCK-WD-2", "MOCK-WD-2-FREEZE", 100123L, new BigDecimal("30"), "USDT", "0x3333");
        store.findWithdraw("MOCK-WD-2").setStatus(io.github.open55.mockupstream.state.WithdrawRecord.Status.CANCEL_PENDING);

        // 全部调用成功
        when(blockchainSimulator.confirmationsOf(anyString())).thenReturn(1L);
        when(blockchainSimulator.currentHeight()).thenReturn(5L);
        when(otxClient.deposit(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));
        when(otxClient.broadcast(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));
        when(otxClient.confirmSettle(anyString())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));
        when(otxClient.cancel(anyString())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));

        ProcessSummary summary = orchestrator.process();

        assertThat(summary.depositBooked()).isEqualTo(2);
        assertThat(summary.settled()).isEqualTo(1);
        assertThat(summary.cancelled()).isEqualTo(1);
        assertThat(summary.remaining()).isZero();
        assertThat(store.listDeposits()).allMatch(d -> d.getStatus() == io.github.open55.mockupstream.state.DepositRecord.Status.BOOKED);
        verify(otxClient, times(2)).deposit(any());
        verify(otxClient, times(1)).confirmSettle(anyString());
        verify(otxClient, times(1)).cancel(anyString());
    }

    /**
     * 单笔充值入账失败：其余事项照常处理，失败项保持挂起可重试。
     */
    @Test
    void process_withOneFailure_continuesOthers() {
        store.registerDeposit("MOCK-DEP-1", 100123L, new BigDecimal("100"), "USDT", "0xtx1");
        store.registerDeposit("MOCK-DEP-2", 100123L, new BigDecimal("200"), "USDT", "0xtx2");
        store.registerWithdraw("MOCK-WD-1", "MOCK-WD-1-FREEZE", 100123L, new BigDecimal("50"), "USDT", "0x3333");

        when(blockchainSimulator.confirmationsOf(anyString())).thenReturn(1L);
        when(blockchainSimulator.currentHeight()).thenReturn(5L);
        // 第一笔入账失败（OTX 业务拒绝），其余成功
        when(otxClient.deposit(any())).thenReturn(new ApiResult(200, "DEPOSIT_REJECTED", "rejected", null, "{}"),
                new ApiResult(200, "200", "success", null, "{}"));
        when(otxClient.broadcast(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));
        when(otxClient.confirmSettle(anyString())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));

        ProcessSummary summary = orchestrator.process();

        assertThat(summary.depositBooked()).isEqualTo(1);
        assertThat(summary.depositFailed()).isEqualTo(1);
        assertThat(summary.settled()).isEqualTo(1);
        assertThat(summary.remaining()).isEqualTo(1);
        // 失败笔保持 CONFIRMING（下次可重试）
        assertThat(store.findDeposit("MOCK-DEP-1").getStatus()).isEqualTo(io.github.open55.mockupstream.state.DepositRecord.Status.CONFIRMING);
        assertThat(store.findDeposit("MOCK-DEP-2").getStatus()).isEqualTo(io.github.open55.mockupstream.state.DepositRecord.Status.BOOKED);
    }

    /**
     * 无挂起事项：零动作、零 OTX 调用。
     */
    @Test
    void process_withNoPending_doesNothing() {
        ProcessSummary summary = orchestrator.process();

        assertThat(summary.total()).isZero();
        verify(otxClient, never()).deposit(any());
        verify(otxClient, never()).broadcast(any());
        verify(otxClient, never()).confirmSettle(anyString());
        verify(otxClient, never()).cancel(anyString());
    }
}
