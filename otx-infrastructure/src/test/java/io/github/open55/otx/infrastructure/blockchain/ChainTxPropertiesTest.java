package io.github.open55.otx.infrastructure.blockchain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChainTxProperties 链上交易配置单元测试。
 * <p>
 * 验证 otx.chain-tx 配置段到属性对象的映射与默认值，以及适配器未知取值的 fail-fast 校验。
 */
@DisplayName("ChainTxProperties 链上交易配置单元测试 | ChainTxProperties unit tests")
class ChainTxPropertiesTest {

    @Nested
    @DisplayName("bind 配置绑定 | bind yaml values")
    class Bind {

        /**
         * 模拟 yaml 的 otx.chain-tx 配置段绑定到属性对象，字段映射正确。
         */
        @Test
        @DisplayName("yaml 配置值映射到属性字段")
        void bind_yamlValues_mapsToProperties() {
            Map<String, String> map = new HashMap<>();
            map.put("otx.chain-tx.required-confirmations", "12");
            map.put("otx.chain-tx.broadcast-adapter", "web3j");
            map.put("otx.chain-tx.signer-adapter", "local-keystore");
            map.put("otx.chain-tx.local-keystore.key-file", "classpath:keystore/dev-withdrawer.json");
            map.put("otx.chain-tx.local-keystore.password", "secret");
            map.put("otx.chain-tx.poll.enabled", "true");
            map.put("otx.chain-tx.poll.interval-ms", "60000");

            Binder binder = new Binder(new MapConfigurationPropertySource(map));
            ChainTxProperties props = binder.bind("otx.chain-tx", Bindable.of(ChainTxProperties.class)).get();

            assertEquals(12, props.getRequiredConfirmations());
            assertEquals("web3j", props.getBroadcastAdapter());
            assertEquals("local-keystore", props.getSignerAdapter());
            assertEquals("classpath:keystore/dev-withdrawer.json", props.getLocalKeystore().getKeyFile());
            assertEquals("secret", props.getLocalKeystore().getPassword());
            assertTrue(props.getPoll().isEnabled());
            assertEquals(60000, props.getPoll().getIntervalMs());
        }

        /**
         * 未配置的字段保持默认值：确认数 12、轮询关闭、间隔 30000。
         */
        @Test
        @DisplayName("未配置字段保持默认值")
        void bind_missingValues_keepDefaults() {
            Binder binder = new Binder(new MapConfigurationPropertySource(Map.of()));
            ChainTxProperties props = binder.bindOrCreate("otx.chain-tx", Bindable.of(ChainTxProperties.class));

            assertEquals(12, props.getRequiredConfirmations());
            assertFalse(props.getPoll().isEnabled());
            assertEquals(30000, props.getPoll().getIntervalMs());
        }
    }

    @Nested
    @DisplayName("validate 启动校验 | fail-fast validation")
    class Validate {

        /**
         * signer-adapter 为未知值（如 kms，仅文档预留）时启动校验抛 IllegalStateException。
         */
        @Test
        @DisplayName("未知 signer-adapter 校验抛异常")
        void validate_unknownSignerAdapter_throwsAtStartup() {
            ChainTxProperties props = new ChainTxProperties();
            props.setSignerAdapter("kms");

            assertThrows(IllegalStateException.class, props::validate);
        }

        /**
         * broadcast-adapter 为未知值时启动校验抛 IllegalStateException。
         */
        @Test
        @DisplayName("未知 broadcast-adapter 校验抛异常")
        void validate_unknownBroadcastAdapter_throwsAtStartup() {
            ChainTxProperties props = new ChainTxProperties();
            props.setBroadcastAdapter("unknown-adapter");

            assertThrows(IllegalStateException.class, props::validate);
        }
    }
}
