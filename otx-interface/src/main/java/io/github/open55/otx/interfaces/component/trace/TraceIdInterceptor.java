package io.github.open55.otx.interfaces.component.trace;

import io.github.open55.otx.common.constant.SymbolConstant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 链路追踪拦截器。
 * <p>
 * 从 HTTP 请求头中提取 traceId，不存在则自动生成 UUID，
 * 存入 SLF4J MDC 实现日志关联，同时设置到响应头中。
 */
public class TraceIdInterceptor implements HandlerInterceptor {

    /**
     * 请求处理前：提取或生成 traceId，存入 MDC 并设置响应头。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        String traceId = request.getHeader(SymbolConstant.TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(SymbolConstant.TRACE_ID, traceId);
        response.setHeader(SymbolConstant.TRACE_ID, traceId);
        return true;
    }

    /**
     * 请求完成后：清除 MDC 中的 traceId。
     */
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
        MDC.remove(SymbolConstant.TRACE_ID);
    }
}
