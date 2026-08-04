package io.github.open55.otx.infrastructure.component.exception;

import io.github.open55.otx.common.exception.BizErrorEnum;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.OptimisticLockException;
import io.github.open55.otx.common.response.Result;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * GlobalExceptionHandler 全局异常处理器单元测试。
 * <p>
 * 覆盖 BizException、未预期异常、PersistenceException（根因为 OptimisticLockException）的响应格式。
 */
@DisplayName("GlobalExceptionHandler 全局异常处理器单元测试 | GlobalExceptionHandler unit tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * BizException 转换为 Result，code 为业务错误码，HTTP 状态为 200。
     */
    @Test
    @DisplayName("BizException 返回业务错误码")
    void handleBizException_returnsErrorCode() {
        BizException ex = BizException.get(BizErrorEnum.ACCOUNT_NOT_EXIST);

        Result<Void> result = handler.handleBizException(ex);

        assertEquals("ACCOUNT_NOT_EXIST", result.getCode());
        assertNotNull(result.getMessage());
    }

    /**
     * 普通 Exception 转换为 Result，code 为 "500"，HTTP 状态为 500。
     */
    @Test
    @DisplayName("普通 Exception 返回 500")
    void handleException_returns500() {
        Exception ex = new RuntimeException("unexpected");

        Result<Void> result = handler.handleException(ex);

        assertEquals("500", result.getCode());
    }

    /**
     * 静态资源/未映射路径不存在（如浏览器自动请求 /favicon.ico）时按 404 返回，
     * 不视为服务器内部错误，避免 ERROR 日志刷屏。
     */
    @Test
    @DisplayName("NoResourceFoundException 返回 404 | missing static resource returns 404")
    void handleNoResourceFound_returns404() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/favicon.ico", "");

        Result<Void> result = handler.handleNoResourceFound(ex);

        assertEquals("404", result.getCode());
    }

    /**
     * PersistenceException 根因为 OptimisticLockException 时返回并发错误码。
     */
    @Test
    @DisplayName("PersistenceException 嵌套 OptimisticLockException 返回 CONCURRENCY_ERROR")
    void handlePersistenceException_withOptimisticLockRootCause_returnsConcurrencyError() {
        OptimisticLockException root = new OptimisticLockException();
        PersistenceException ex = new PersistenceException("persistence issue", root);

        Result<Void> result = handler.handlePersistenceException(ex);

        assertEquals("CONCURRENCY_ERROR", result.getCode());
    }

    /**
     * MyBatisSystemException 根因为 OptimisticLockException 时返回并发错误码。
     */
    @Test
    @DisplayName("MyBatisSystemException 嵌套 OptimisticLockException 返回 CONCURRENCY_ERROR")
    void handleMyBatisSystemException_withOptimisticLockRootCause_returnsConcurrencyError() {
        OptimisticLockException root = new OptimisticLockException();
        MyBatisSystemException ex = new MyBatisSystemException(root);

        Result<Void> result = handler.handlePersistenceException(ex);

        assertEquals("CONCURRENCY_ERROR", result.getCode());
    }

    /**
     * PersistenceException 根因不是 OptimisticLockException 时返回 500。
     */
    @Test
    @DisplayName("PersistenceException 普通嵌套返回 500")
    void handlePersistenceException_withOtherRootCause_returns500() {
        PersistenceException ex = new PersistenceException("db error", new RuntimeException("other"));

        Result<Void> result = handler.handlePersistenceException(ex);

        assertEquals("500", result.getCode());
    }
}
