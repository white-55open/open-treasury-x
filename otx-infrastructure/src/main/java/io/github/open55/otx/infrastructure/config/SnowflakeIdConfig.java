package io.github.open55.otx.infrastructure.config;

import io.github.open55.otx.infrastructure.component.id.SnowflakeIdGeneratorImpl;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@ConfigurationProperties(prefix = "snowflake-id")
public class SnowflakeIdConfig {
    private long workerId;

    private long dataCenterId;

    @Bean
    public SnowflakeIdGeneratorImpl snowflakeIdGenerator() {
        return new SnowflakeIdGeneratorImpl(workerId, dataCenterId);
    }
}