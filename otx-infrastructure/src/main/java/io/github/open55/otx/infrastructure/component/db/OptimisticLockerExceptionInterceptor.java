package io.github.open55.otx.infrastructure.component.db;

import io.github.open55.otx.common.exception.OptimisticLockException;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

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