package com.tracespring.config;

import com.tracespring.interceptor.LifecycleInterceptor;
import com.tracespring.service.TraceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TraceStore traceStore;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LifecycleInterceptor(traceStore))
                .addPathPatterns("/api/**", "/debug/**");
    }
}
