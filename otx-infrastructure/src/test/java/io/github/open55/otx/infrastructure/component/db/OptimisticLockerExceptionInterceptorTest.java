package io.github.open55.otx.infrastructure.component.db;

import io.github.open55.otx.common.exception.OptimisticLockException;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * OptimisticLockerExceptionInterceptor MyBatis 拦截器单元测试。
 * <p>
 * 验证 update 影响行数为 0 时抛出 OptimisticLockException。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OptimisticLockerExceptionInterceptor 拦截器单元测试 | OptimisticLockerExceptionInterceptor unit tests")
class OptimisticLockerExceptionInterceptorTest {

    @Mock
    private Invocation invocation;

    /**
     * update 返回 0 行时抛出 OptimisticLockException。
     */
    @Test
    @DisplayName("update 返回 0 行抛 OptimisticLockException")
    void intercept_withZeroRows_throwsOptimisticLock() throws Throwable {
        when(invocation.proceed()).thenReturn(0);
        OptimisticLockerExceptionInterceptor interceptor = new OptimisticLockerExceptionInterceptor();

        assertThrows(OptimisticLockException.class, () -> interceptor.intercept(invocation));
    }

    /**
     * update 返回 1 行时正常返回，不抛异常。
     */
    @Test
    @DisplayName("update 返回 1 行不抛异常")
    void intercept_withOneRow_returnsNormally() throws Throwable {
        when(invocation.proceed()).thenReturn(1);
        OptimisticLockerExceptionInterceptor interceptor = new OptimisticLockerExceptionInterceptor();

        interceptor.intercept(invocation);
    }
}
