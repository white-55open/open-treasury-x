package io.github.open55.mockupstream.scenario;

import io.github.open55.mockupstream.blockchain.BlockchainSimulator;
import io.github.open55.mockupstream.blockchain.SimulatedTx;
import io.github.open55.mockupstream.config.SimulatorProperties;
import io.github.open55.mockupstream.state.DepositRecord;
import io.github.open55.mockupstream.state.RegistryStore;
import io.github.open55.mockupstream.upstream.ApiResult;
import io.github.open55.mockupstream.upstream.OtxClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 充值业务编排器单测。
 * <p>
 * 覆盖范围：发起充值生成独立业务号与模拟交易、确认入账成功置 BOOKED、
 * 入账失败保持 CONFIRMING（支持重试）、操作日志记录端点与响应。
 */
class DepositSimulatorTest {

    /**
     * 被测对象
     */
    private DepositSimulator depositSimulator;

    /**
     * 模拟链 mock
     */
    private BlockchainSimulator blockchainSimulator;

    /**
     * OTX 客户端 mock
     */
    private OtxClient otxClient;

    /**
     * 真实内存注册表
     */
    private RegistryStore store;

    /**
     * 模拟器配置（确认阈值 12）
     */
    private SimulatorProperties properties;

    /**
     * 每次测试前重建被测对象与依赖。
     */
    @BeforeEach
    void setUp() {
        blockchainSimulator = mock(BlockchainSimulator.class);
        otxClient = mock(OtxClient.class);
        store = new RegistryStore();
        properties = new SimulatorProperties();
        properties.setRequiredConfirmations(12);
        depositSimulator = new DepositSimulator(blockchainSimulator, otxClient, store, properties);
    }

    /**
     * 发起充值：生成独立 bizNo 的确认中记录，并调用幂等开户。
     */
    @Test
    void initiateDeposit_createsConfirmingRecordWithUniqueBizNo() {
        when(blockchainSimulator.registerTx(anyString(), anyString(), any()))
                .thenReturn(new SimulatedTx("0xtx1", "0xfrom", "0xto",
                        BigDecimal.ONE.toBigInteger(), 0, "0x1"));

        DepositRecord first = depositSimulator.initiateDeposit(100123L, new BigDecimal("100"));
        DepositRecord second = depositSimulator.initiateDeposit(100123L, new BigDecimal("200"));

        assertThat(first.getStatus()).isEqualTo(DepositRecord.Status.CONFIRMING);
        assertThat(second.getStatus()).isEqualTo(DepositRecord.Status.CONFIRMING);
        assertThat(first.getBizNo()).isNotEqualTo(second.getBizNo());
        // 每次发起均幂等开户
        verify(otxClient, times(2)).createAccount(100123L);
    }

    /**
     * 确认入账成功：推进确认数至达标、调用 POST /deposit、记录置 BOOKED。
     */
    @Test
    void bookDeposit_success_marksBookedAndCallsDeposit() {
        DepositRecord record = store.registerDeposit("MOCK-DEP-1", 100123L,
                new BigDecimal("100"), "USDT", "0xtx1");
        when(blockchainSimulator.confirmationsOf("0xtx1")).thenReturn(1L);
        when(blockchainSimulator.currentHeight()).thenReturn(5L);
        when(otxClient.deposit(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));

        boolean booked = depositSimulator.bookDeposit(record);

        assertThat(booked).isTrue();
        assertThat(record.getStatus()).isEqualTo(DepositRecord.Status.BOOKED);
        // 推进到 createdBlock + 阈值 - 1 的高度（当前 5 + 缺 11 = 16）
        verify(blockchainSimulator).advanceTo(16L);
        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(otxClient).deposit(bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).containsEntry("bizNo", "MOCK-DEP-1");
    }

    /**
     * 确认入账失败：记录保持 CONFIRMING（下次一键处理可重试）。
     */
    @Test
    void bookDeposit_failure_keepsConfirming() {
        DepositRecord record = store.registerDeposit("MOCK-DEP-2", 100123L,
                new BigDecimal("100"), "USDT", "0xtx2");
        when(blockchainSimulator.confirmationsOf("0xtx2")).thenReturn(1L);
        when(blockchainSimulator.currentHeight()).thenReturn(5L);
        // OTX 业务失败（如入账被拒）
        when(otxClient.deposit(any())).thenReturn(new ApiResult(200, "DEPOSIT_REJECTED", "rejected", null, "{}"));

        boolean booked = depositSimulator.bookDeposit(record);

        assertThat(booked).isFalse();
        assertThat(record.getStatus()).isEqualTo(DepositRecord.Status.CONFIRMING);
    }

    /**
     * 确认数已达标时重试：推进为空操作，仍再次调用入账（可重试语义）。
     */
    @Test
    void bookDeposit_whenAlreadyConfirmed_retriesDepositWithoutAdvance() {
        DepositRecord record = store.registerDeposit("MOCK-DEP-3", 100123L,
                new BigDecimal("100"), "USDT", "0xtx3");
        // 确认数已达阈值
        when(blockchainSimulator.confirmationsOf("0xtx3")).thenReturn(12L);
        when(blockchainSimulator.currentHeight()).thenReturn(20L);
        when(otxClient.deposit(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));

        depositSimulator.bookDeposit(record);

        // 目标高度等于当前高度（无推进动作）
        verify(blockchainSimulator).advanceTo(20L);
        verify(otxClient).deposit(any());
    }

    /**
     * 操作日志随入账动作记录：包含端点 POST /deposit 与响应摘要。
     */
    @Test
    void bookDeposit_appendsOperationLog() {
        DepositRecord record = store.registerDeposit("MOCK-DEP-4", 100123L,
                new BigDecimal("100"), "USDT", "0xtx4");
        when(blockchainSimulator.confirmationsOf("0xtx4")).thenReturn(12L);
        when(blockchainSimulator.currentHeight()).thenReturn(20L);
        when(otxClient.deposit(any())).thenReturn(new ApiResult(200, "200", "success", null, "{}"));

        depositSimulator.bookDeposit(record);

        assertThat(store.recentLogs()).isNotEmpty();
        assertThat(store.recentLogs().get(0).getEndpoint()).isEqualTo("POST /deposit");
        verify(otxClient, never()).confirmSettle(anyString());
    }
}
