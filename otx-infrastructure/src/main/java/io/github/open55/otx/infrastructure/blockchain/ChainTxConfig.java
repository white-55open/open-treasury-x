package io.github.open55.otx.infrastructure.blockchain;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 链上交易配置注册类。
 * <p>
 * 注册 ChainTxProperties 到 Spring 容器（对应 application.yaml 的 otx.chain-tx.* 配置段），
 * 启动时由 @PostConstruct 校验触发适配器取值的 fail-fast 检查。
 */
@Configuration
@EnableConfigurationProperties(ChainTxProperties.class)
public class ChainTxConfig {
}
