package io.github.open55.mockupstream.state;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存状态注册表单测。
 * <p>
 * 覆盖范围：操作日志环形缓冲的追加/读取与上限裁剪、重复 uid 创建幂等、
 * 并发创建用户不产生重复记录，以及充值/提现记录的注册与查询。
 */
class RegistryStoreTest {

    /**
     * 重复创建同一 uid：第二次返回已有用户实例（幂等，不重复注册）。
     */
    @Test
    void createUser_withSameUidTwice_returnsSameInstance() {
        RegistryStore store = new RegistryStore();
        SimUser first = store.createUser(100123L);
        SimUser second = store.createUser(100123L);

        assertThat(first).isSameAs(second);
        assertThat(store.listUsers()).hasSize(1);
    }

    /**
     * 并发创建同一 uid：仅产生一个用户（ConcurrentHashMap.computeIfAbsent 原子性）。
     */
    @Test
    void createUser_concurrentSameUid_createsOnlyOne() throws InterruptedException {
        RegistryStore store = new RegistryStore();
        int threadCount = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            // 并发线程同时创建同一 uid 的用户
            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    store.createUser(888L);
                    return null;
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertThat(store.listUsers()).hasSize(1);
    }

    /**
     * 充值/提现记录注册后可按业务号查询，初始状态正确。
     */
    @Test
    void registerDepositAndWithdraw_recordsAreQueryableWithInitialStatus() {
        RegistryStore store = new RegistryStore();
        store.createUser(100123L);

        DepositRecord deposit = store.registerDeposit("MOCK-DEP-1", 100123L,
                new BigDecimal("100"), "USDT", "0xabc");
        WithdrawRecord withdraw = store.registerWithdraw("MOCK-WD-1", "MOCK-WD-1-FREEZE",
                100123L, new BigDecimal("50"), "USDT", "0x3333");

        assertThat(store.findDeposit("MOCK-DEP-1")).isSameAs(deposit);
        assertThat(deposit.getStatus()).isEqualTo(DepositRecord.Status.CONFIRMING);
        assertThat(store.findWithdraw("MOCK-WD-1")).isSameAs(withdraw);
        assertThat(withdraw.getStatus()).isEqualTo(WithdrawRecord.Status.FROZEN);
        assertThat(store.listDeposits()).hasSize(1);
        assertThat(store.listWithdraws()).hasSize(1);
    }

    /**
     * 日志追加后按倒序读取（最新在前），超过 200 条上限时裁剪最旧条目。
     */
    @Test
    void appendLog_beyondCapacity_trimsOldestEntries() {
        RegistryStore store = new RegistryStore();

        // 追加 250 条日志，验证只保留最近 200 条且倒序排列
        for (int i = 0; i < 250; i++) {
            store.appendLog(new OperationLogEntry(i, "actor", "desc-" + i, "endpoint", "resp"));
        }
        List<OperationLogEntry> logs = store.recentLogs();

        assertThat(logs).hasSize(200);
        assertThat(logs.get(0).getDescription()).isEqualTo("desc-249");
        assertThat(logs.get(199).getDescription()).isEqualTo("desc-50");
    }
}
