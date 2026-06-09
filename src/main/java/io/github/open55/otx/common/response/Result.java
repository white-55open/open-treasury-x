package io.github.open55.otx.common.response;

import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
public class Result<T> {
    private String code;
    private String message;
    private T data;
    private long timestamp;
    private String traceId;

    public Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.traceId = org.slf4j.MDC.get("traceId");
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>(String.valueOf(HttpStatus.OK.value()), "success", data);
    }

    public static <T> Result<T> fail(String errorCode, String message, T data) {
        return new Result<T>(errorCode, message, data);
    }
}
