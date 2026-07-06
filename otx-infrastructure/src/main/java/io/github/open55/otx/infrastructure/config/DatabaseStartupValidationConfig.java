package io.github.open55.otx.infrastructure.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据库启动校验配置。
 * <p>
 * 应用启动时验证数据库连接是否可用，连接失败则抛出异常阻止启动。
 */
@Configuration
public class DatabaseStartupValidationConfig {

    /**
     * 注册启动校验器，在应用启动完成后立即验证数据库连接。
     *
     * @param dataSource 数据源
     * @return 启动校验器
     */
    @Bean
    public SmartInitializingSingleton databaseStartupValidator(DataSource dataSource) {
        return () -> {
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(5)) {
                    throw new IllegalStateException("Database connection is invalid");
                }
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to connect to database at startup", ex);
            }
        };
    }
}
