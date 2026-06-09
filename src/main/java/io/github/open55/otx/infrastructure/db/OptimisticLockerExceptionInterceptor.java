package io.github.open55.otx.infrastructure.db;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import io.github.open55.otx.common.exception.OptimisticLockException;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;

import java.sql.Statement;

@Intercepts({
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})
})
public class OptimisticLockerExceptionInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();

        if (result instanceof Integer) {
            int rows = (Integer) result;
            if (rows == 0) {
                throw new OptimisticLockException();
            }
        }
        return result;
    }
}