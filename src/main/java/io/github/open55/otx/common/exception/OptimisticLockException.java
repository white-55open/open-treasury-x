package io.github.open55.otx.common.exception;

public class OptimisticLockException extends BizException {

    public OptimisticLockException() {
        super(BizErrorEnum.CONCURRENCY_ERROR);
    }
}