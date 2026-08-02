package io.github.open55.otx.infrastructure.blockchain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Web3jProperties web3j 连接配置单元测试。
 * <p>
 * 覆盖充值入账安全确认数配置项的默认值与自定义值绑定。
 * English: Unit tests for the web3j connection properties, covering the
 * default and custom binding of the deposit confirmation count.
 */
@DisplayName("Web3jProperties web3j 连接配置单元测试 | Web3jProperties unit tests")
class Web3jPropertiesTest {

    /** 配置默认安全确认数 */
    private static final int DEFAULT_CONFIRMATIONS_12 = 12;

    /** 自定义安全确认数 */
    private static final int CUSTOM_CONFIRMATIONS_6 = 6;

    /**
     * 场景：新建配置对象未显式赋值时，requiredConfirmations 取默认值 12。
     * Scenario: a fresh properties object defaults requiredConfirmations to 12.
     */
    @Test
    @DisplayName("requiredConfirmations 默认值为 12 | requiredConfirmations defaults to 12")
    void web3jProperties_requiredConfirmations_defaultsTo12() {
        // Arrange：构造未显式赋值的配置对象
        Web3jProperties properties = new Web3jProperties();

        // Act & Assert：断言默认安全确认数
        assertEquals(DEFAULT_CONFIRMATIONS_12, properties.getRequiredConfirmations());
    }

    /**
     * 场景：配置绑定自定义值后，requiredConfirmations 返回该自定义值。
     * Scenario: after binding a custom value, requiredConfirmations returns it.
     */
    @Test
    @DisplayName("requiredConfirmations 可绑定自定义值 | requiredConfirmations binds a custom value")
    void web3jProperties_requiredConfirmations_bindsCustomValue() {
        // Arrange：构造配置对象并设置自定义确认数
        Web3jProperties properties = new Web3jProperties();
        properties.setRequiredConfirmations(CUSTOM_CONFIRMATIONS_6);

        // Act & Assert：断言自定义值生效
        assertEquals(CUSTOM_CONFIRMATIONS_6, properties.getRequiredConfirmations());
    }
}
