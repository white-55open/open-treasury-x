package io.github.open55.otx.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BizException 业务异常单元测试。
 * <p>
 * 覆盖工厂方法、错误码获取、异常继承链。
 */
@DisplayName("BizException 业务异常单元测试 | BizException unit tests")
class BizExceptionTest {

    /**
     * get 工厂方法创建异常，错误码和消息正确。
     */
    @Test
    @DisplayName("get 工厂方法正确创建异常")
    void get_returnsExceptionWithCorrectCode() {
        BizException ex = BizException.get(BizErrorEnum.ACCOUNT_NOT_EXIST);
        assertEquals("ACCOUNT_NOT_EXIST", ex.getErrorCode());
        assertNotNull(ex.getMessage());
    }

    /**
     * BizException 继承 RuntimeException。
     */
    @Test
    @DisplayName("BizException 继承 RuntimeException")
    void isSubclassOfRuntimeException() {
        BizException ex = BizException.get(BizErrorEnum.INSUFFICIENT_BALANCE);
        assertInstanceOf(RuntimeException.class, ex);
    }

    /**
     * 不同错误码创建不同异常。
     */
    @Test
    @DisplayName("不同 BizErrorEnum 生成不同错误码")
    void differentEnums_produceDifferentCodes() {
        BizException ex1 = BizException.get(BizErrorEnum.ACCOUNT_NOT_EXIST);
        BizException ex2 = BizException.get(BizErrorEnum.INSUFFICIENT_BALANCE);
        assertNotEquals(ex1.getErrorCode(), ex2.getErrorCode());
    }

    /**
     * OptimisticLockException 继承 BizException。
     */
    @Test
    @DisplayName("OptimisticLockException 继承 BizException")
    void optimisticLockException_isBizException() {
        OptimisticLockException ex = new OptimisticLockException();
        assertInstanceOf(BizException.class, ex);
        assertEquals("CONCURRENCY_ERROR", ex.getErrorCode());
    }
}
