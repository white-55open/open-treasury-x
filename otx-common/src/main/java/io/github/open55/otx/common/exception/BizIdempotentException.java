package io.github.open55.otx.common.exception;

public class BizIdempotentException extends RuntimeException {
    public BizIdempotentException(Throwable cause) {
        super(cause);
    }

    public BizIdempotentException() {
    }
}
