package io.github.open55.mockupstream.upstream;

import tools.jackson.databind.JsonNode;

/**
 * OTX 调用结果（Result JSON 的轻量解析视图）。
 * <p>
 * OTX 统一响应结构：HTTP 200 + {@code {"code":"200","message":"success","data":...}}，
 * 业务失败时 code 为业务错误码、message 为错误说明。连接失败（OTX 未启动）时
 * httpStatus=-1 且无响应体。
 *
 * @param httpStatus HTTP 状态码（连接失败时为 -1）
 * @param code       业务码（Result.code，成功恒为 "200"）
 * @param message    业务消息（Result.message）
 * @param data       业务数据节点（Result.data，可为 null/缺失）
 * @param rawBody    原始响应体
 */
public record ApiResult(int httpStatus, String code, String message, JsonNode data, String rawBody) {

    /**
     * 判断调用是否成功：OTX 业务码为 "200" 且连接成功。
     *
     * @return true 表示业务成功
     */
    public boolean isSuccess() {
        return "200".equals(code);
    }

    /**
     * 构造连接失败结果（OTX 不可达时的统一失败形态）。
     *
     * @param errorMessage 失败原因
     * @return 连接失败结果
     */
    public static ApiResult connectionFailure(String errorMessage) {
        return new ApiResult(-1, "", errorMessage, null, "");
    }
}
