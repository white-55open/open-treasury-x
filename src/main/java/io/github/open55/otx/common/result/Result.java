package io.github.open55.otx.common.result;

public record Result<T>(
        Integer code,
        String message,
        T data
) {
}
