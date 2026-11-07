package com.dora.flowerstore.config;

import com.dora.flowerstore.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 所有 API 都經過攔截器：設定 userId，並檢查需要登入 / 管理員的路徑
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }
}
