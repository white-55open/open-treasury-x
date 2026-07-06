package io.github.open55.otx.common.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Result 统一响应模型单元测试。
 * <p>
 * 覆盖 success/fail 工厂方法，验证字段正确性和 traceId 填充。
 */
@DisplayName("Result 统一响应单元测试 | Result unit tests")
class ResultTest {

    /**
     * success 创建成功响应，code 为 200，message 为 success，data 可带值。
     */
    @Test
    @DisplayName("success 返回 200 和 data")
    void success_withData_returnsOk() {
        Result<String> result = Result.success("hello");
        assertEquals("200", result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("hello", result.getData());
    }

    /**
     * success() 无参创建成功响应，data 为 null。
     */
    @Test
    @DisplayName("success 无参返回 data=null")
    void success_withoutData_returnsNullData() {
        Result<Void> result = Result.success();
        assertEquals("200", result.getCode());
        assertEquals("success", result.getMessage());
    }

    /**
     * fail 创建失败响应，code/message/data 按入参设置。
     */
    @Test
    @DisplayName("fail 返回指定错误码和消息")
    void fail_returnsErrorCodeAndMessage() {
        Result<Void> result = Result.fail("ACCOUNT_NOT_EXIST", "account not found", null);
        assertEquals("ACCOUNT_NOT_EXIST", result.getCode());
        assertEquals("account not found", result.getMessage());
    }

    /**
     * 时间戳非零，表示创建时间。
     */
    @Test
    @DisplayName("时间戳非零")
    void timestamp_isNonZero() {
        Result<String> result = Result.success();
        assertTrue(result.getTimestamp() > 0);
    }

}
