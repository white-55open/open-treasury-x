package io.github.open55.otx.common.exception;

import cn.hutool.core.exceptions.ExceptionUtil;
import io.github.open55.otx.common.constant.WarningWordConstant;
import io.github.open55.otx.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.mybatis.spring.MyBatisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
     * http 500
     *
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception ex) {
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