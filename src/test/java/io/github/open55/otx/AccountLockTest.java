package io.github.open55.otx;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.open55.otx.account.entity.AccountEntity;
import io.github.open55.otx.account.mapper.AccountMapper;
import io.github.open55.otx.account.service.AccountService;
import io.github.open55.otx.common.exception.OptimisticLockException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest
public class AccountLockTest {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountService accountService;


    @Test
    public void testAccountOptimisticLockConflict() throws Exception {
        Long testUid = 999999L;
        AtomicReference<Throwable> threadBError = new AtomicReference<>();
        CountDownLatch startUpdateSignal = new CountDownLatch(1);
        accountService.createAccount(testUid);

        // 2. 线程 A：同时读取 -> 阻塞 -> 执行扣款 100
        CompletableFuture<Void> futureA = CompletableFuture.runAsync(() -> {
            try {
                AccountEntity accountA = accountMapper.selectOne(new LambdaQueryWrapper<AccountEntity>().eq(AccountEntity::getUid, testUid));
                startUpdateSignal.await();
                accountA.setAvailableBalance(accountA.getAvailableBalance().subtract(new BigDecimal("100")));
                accountMapper.updateById(accountA);
            } catch (Exception e) {
                threadBError.set(ExceptionUtil.getRootCause(e));
            }
        });

        CompletableFuture<Void> futureB = CompletableFuture.runAsync(() -> {
            try {
                AccountEntity accountB = accountMapper.selectOne(new LambdaQueryWrapper<AccountEntity>().eq(AccountEntity::getUid, testUid));
                startUpdateSignal.await();
                Thread.sleep(50);
                accountB.setAvailableBalance(accountB.getAvailableBalance().subtract(new BigDecimal("50")));
                accountB.setFrozenBalance(accountB.getFrozenBalance().add(new BigDecimal("50")));
                accountMapper.updateById(accountB);
            } catch (Exception e) {
                threadBError.set(ExceptionUtil.getRootCause(e));
            }
        });

        startUpdateSignal.countDown();
        CompletableFuture.allOf(futureA, futureB).join();
        Assertions.assertNotNull(threadBError.get(), "Optimistic locking exception not triggered.");
        Assertions.assertInstanceOf(OptimisticLockException.class, threadBError.get(), "The optimistic locking exception type does not match expectations." + threadBError.get().getClass().getName());
    }
}
