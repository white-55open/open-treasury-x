package io.github.open55.otx.infrastructure.component.exception;

import cn.hutool.core.exceptions.ExceptionUtil;
import io.github.open55.otx.common.constant.WarningWordConstant;
import io.github.open55.otx.common.exception.BizException;
import io.github.open55.otx.common.exception.OptimisticLockException;
import io.github.open55.otx.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * http 200 but biz error
     *
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBizException(BizException ex) {
        return Result.fail(ex.getErrorCode(), ex.getMessage(), null);
    }


    /**
     * 静态资源/未映射路径不存在（如浏览器自动请求 /favicon.ico），
     * 按 404 返回，不视为服务器内部错误（避免 ERROR 日志刷屏）。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException ex) {
        // 资源不存在属于客户端请求问题，记录 DEBUG 级即可
        log.debug("Static resource or mapped path not found: {}", ex.getResourcePath());
        return Result.fail(String.valueOf(HttpStatus.NOT_FOUND.value()),
                HttpStatus.NOT_FOUND.toString(), null);
    }

    /**
     * http 500
     *
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception ex) {
        // 未捕获的服务器内部异常，记录完整堆栈供排查
        log.error(WarningWordConstant.SERVER_ERROR, ex);
        return Result.fail(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), HttpStatus.INTERNAL_SERVER_ERROR.toString(), null);
    }

    /**
     * OptimisticLockException inspect
     *
     */
    @ExceptionHandler({MyBatisSystemException.class, PersistenceException.class})
    public Result<Void> handlePersistenceException(Exception e) {
        Throwable rootCause = ExceptionUtil.getRootCause(e);
        if (rootCause instanceof OptimisticLockException) {
            return handleBizException((OptimisticLockException) rootCause);
        }

        return handleException(e);
    }
}