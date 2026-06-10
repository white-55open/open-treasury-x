package io.github.open55.otx.exception;

public class BizException extends RuntimeException {
    protected final BizErrorEnum bizError;

    public BizException(BizErrorEnum bizError) {
        this(null, bizError);
    }

    public BizException(Throwable cause, BizErrorEnum bizError) {
        super(bizError.getMessage(), cause);
        this.bizError = bizError;
    }

    public static BizException get(BizErrorEnum bizErrorEnum) {
        return new BizException(bizErrorEnum);
    }

    public String getErrorCode() {
        return bizError.getCode();
    }

}