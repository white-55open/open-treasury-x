package io.github.open55.otx.exception;

public class OptimisticLockException extends BizException {

    public OptimisticLockException() {
        super(BizErrorEnum.CONCURRENCY_ERROR);
    }
}