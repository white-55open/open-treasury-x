package io.github.open55.otx.infrastructure.config;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseStartupValidationConfig {

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
