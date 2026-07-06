package io.github.open55.otx.infrastructure.config;

import io.github.open55.otx.infrastructure.component.id.SnowflakeIdGeneratorImpl;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 雪花 ID 自动配置。
 * <p>
 * 从 application.yaml 的 snowflake-id.worker-id 和 snowflake-id.data-center-id 读取配置，
 * 构造 SnowflakeIdGeneratorImpl 实例。
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "snowflake-id")
public class SnowflakeIdConfig {

    /**
     * 工作节点 ID，范围 0～31
     */
    private long workerId;

    /**
     * 数据中心 ID，范围 0～31
     */
    private long dataCenterId;

    /**
     * 构造雪花 ID 生成器 Bean。
     *
     * @return 雪花 ID 生成器实例
     */
    @Bean
    public SnowflakeIdGeneratorImpl snowflakeIdGenerator() {
        return new SnowflakeIdGeneratorImpl(workerId, dataCenterId);
    }
}