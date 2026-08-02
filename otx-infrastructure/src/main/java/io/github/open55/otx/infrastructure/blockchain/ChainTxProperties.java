package io.github.open55.otx.infrastructure.blockchain;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * 链上交易（签名/广播）配置。
 * <p>
 * 从 application.yaml 的 otx.chain-tx.* 读取，包含结算所需确认数、广播适配器、
 * 签名适配器、本地 keystore（dev-only）与自动结算轮询配置。
 */
@Data
@ConfigurationProperties(prefix = "otx.chain-tx")
public class ChainTxProperties {

    /**
     * 支持的签名适配器集合（当前仅 local-keystore，dev-only；kms / mpc / fireblocks / cobo 仅文档预留不实现）
     */
    private static final Set<String> SUPPORTED_SIGNER_ADAPTERS = Set.of("local-keystore");

    /**
     * 支持的广播适配器集合（当前仅 web3j）
     */
    private static final Set<String> SUPPORTED_BROADCAST_ADAPTERS = Set.of("web3j");

    /**
     * 结算所需链上确认数，默认 12
     */
    private int requiredConfirmations = 12;

    /**
     * 广播适配器选择，当前仅 web3j
     */
    private String broadcastAdapter;

    /**
     * 签名适配器选择，当前仅 local-keystore（dev-only，生产环境对接外部签名设施）
     */
    private String signerAdapter;

    /**
     * 本地 keystore 配置（仅 signer-adapter=local-keystore 时生效）
     */
    private LocalKeystore localKeystore = new LocalKeystore();

    /**
     * 自动结算轮询配置（默认关闭）
     */
    private Poll poll = new Poll();

    /**
     * 启动时校验适配器取值，未知值 fail-fast 拒绝启动。
     *
     * @throws IllegalStateException 当 signerAdapter / broadcastAdapter 不在支持的集合内时抛出
     */
    @PostConstruct
    public void validate() {
        // 校验签名适配器：未知取值直接拒绝启动，防止配置错误在生产环境静默生效
        if (signerAdapter != null && !SUPPORTED_SIGNER_ADAPTERS.contains(signerAdapter)) {
            throw new IllegalStateException("Unsupported signer-adapter: " + signerAdapter
                    + ", supported adapters: " + SUPPORTED_SIGNER_ADAPTERS);
        }
        // 校验广播适配器：未知取值直接拒绝启动
        if (broadcastAdapter != null && !SUPPORTED_BROADCAST_ADAPTERS.contains(broadcastAdapter)) {
            throw new IllegalStateException("Unsupported broadcast-adapter: " + broadcastAdapter
                    + ", supported adapters: " + SUPPORTED_BROADCAST_ADAPTERS);
        }
    }

    /**
     * 本地 keystore 配置（dev-only）。
     * <p>
     * 私钥保存在本地 keystore 文件中，仅限开发/演示环境使用；
     * 密码经环境变量注入（OTX_KEYSTORE_PASSWORD），禁止硬编码。
     */
    @Data
    public static class LocalKeystore {

        /**
         * keystore 文件路径，支持 classpath: 前缀（如 classpath:keystore/dev-withdrawer.json）
         */
        private String keyFile;

        /**
         * keystore 密码，经环境变量注入，禁止硬编码
         */
        private String password;
    }

    /**
     * 自动结算轮询配置。
     * <p>
     * 启用后定时扫描已广播的提现请求并执行链上确认结算（本期默认关闭）。
     */
    @Data
    public static class Poll {

        /**
         * 是否启用轮询，默认关闭
         */
        private boolean enabled = false;

        /**
         * 轮询间隔（毫秒），默认 30000
         */
        private long intervalMs = 30000;
    }
}
