package io.github.open55.otx.common.result;

public final class Results {

    public static <T> Result<T> success(T data) {
        return new Result<T>(0, "success", data);
    }
}