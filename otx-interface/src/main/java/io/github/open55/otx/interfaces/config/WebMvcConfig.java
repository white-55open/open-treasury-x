package io.github.open55.otx.interfaces.config;

import io.github.open55.otx.interfaces.component.trace.TraceIdInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMVC 配置。
 * <p>
 * 注册 TraceIdInterceptor 到所有请求路径，实现链路追踪。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    /**
     * 注册拦截器：为所有 HTTP 请求添加 TraceIdInterceptor。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceIdInterceptor()).addPathPatterns("/**");
    }
}