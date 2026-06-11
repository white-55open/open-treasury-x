package io.github.open55.otx.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import io.github.open55.otx.component.db.OptimisticLockerExceptionInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisConfig {

    /**
     * MybatisPlusInterceptor based in mybatis-plus
     *
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * OptimisticLockerExceptionInterceptor based in mybatis
     *
     */
    @Bean
    public OptimisticLockerExceptionInterceptor optimisticLockerExceptionInterceptor() {
        return new OptimisticLockerExceptionInterceptor();
    }
}
