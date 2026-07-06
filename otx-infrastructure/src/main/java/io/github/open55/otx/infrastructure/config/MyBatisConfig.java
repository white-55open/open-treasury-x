package io.github.open55.otx.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import io.github.open55.otx.infrastructure.component.db.OptimisticLockerExceptionInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类。
 * <p>
 * 注册 MybatisPlusInterceptor（乐观锁、分页等插件）
 * 和自定义的 OptimisticLockerExceptionInterceptor（将更新影响行数为 0 转为异常）。
 */
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
