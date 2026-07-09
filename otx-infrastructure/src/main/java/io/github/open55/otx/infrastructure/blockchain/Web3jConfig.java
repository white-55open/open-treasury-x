package io.github.open55.otx.infrastructure.blockchain;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import okhttp3.OkHttpClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * web3j 客户端池化配置。
 * <p>
 * 启动时按每个 RPC URL 创建一个 Web3j 共享实例（OkHttpClient 线程安全，可并发调用），
 * 应用关闭时通过 @PreDestroy 优雅关闭所有实例。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(Web3jProperties.class)
public class Web3jConfig {

    private final Web3jProperties properties;

    private Map<String, Web3j> pool;

    /**
     * 创建 Web3j 客户端池。
     * <p>
     * key 为 RPC URL，value 为对应的 Web3j 共享实例。
     * 使用 LinkedHashMap 保持与配置中 rpcUrls 一致的顺序，便于故障切换按序回退。
     *
     * @return Web3j 客户端池
     */
    @Bean
    public Map<String, Web3j> web3jPool() {
        Map<String, Web3j> pool = new LinkedHashMap<>();
        for (String url : properties.getRpcUrls()) {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(properties.getReadTimeout(), TimeUnit.MILLISECONDS)
                    .readTimeout(properties.getReadTimeout(), TimeUnit.MILLISECONDS)
                    .build();
            Web3j web3j = Web3j.build(new HttpService(url, okHttpClient));
            pool.put(url, web3j);
            // Web3j 客户端创建成功，记录对应的 RPC URL
            log.info("Web3j client created, RPC URL: {}", url);
        }
        if (pool.isEmpty()) {
            // 未配置 RPC URL，客户端池为空，后续链上调用将全部失败
            log.warn("No RPC URLs configured, web3j client pool is empty");
        }
        this.pool = pool;
        return pool;
    }

    /**
     * 优雅关闭所有 Web3j 客户端实例，释放连接池和线程资源。
     */
    @PreDestroy
    public void shutdown() {
        if (pool == null) {
            return;
        }
        for (Map.Entry<String, Web3j> entry : pool.entrySet()) {
            try {
                entry.getValue().shutdown();
                // Web3j 客户端优雅关闭，释放连接池和线程资源
                log.info("Web3j client shut down, RPC URL: {}", entry.getKey());
            } catch (Exception e) {
                // Web3j 客户端关闭失败，记录异常但不影响其他客户端关闭
                log.error("Web3j client shutdown failed, RPC URL: {}", entry.getKey(), e);
            }
        }
    }
}
