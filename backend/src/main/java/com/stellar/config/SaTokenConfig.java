package com.stellar.config;

import com.stellar.interceptor.AuthInterceptor;
import com.stellar.interceptor.RateLimitInterceptor;
import com.stellar.interceptor.RequestLogInterceptor;
import com.stellar.infra.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final RateLimitService rateLimitService;

    /**
     * 鉴权拦截器：默认所有接口要求登录，仅标注 {@link com.stellar.common.annotation.PublicAccess}
     * 的方法/类对游客放行。放行的公开接口仍应在方法内用 {@code StpUtil.isLogin()} 区分游客，
     * 并叠加 IP 限流（阶段3），避免裸奔。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 请求日志 + traceId：最先执行（preHandle 设 traceId，afterCompletion 记耗时）
        registry.addInterceptor(new RequestLogInterceptor())
                .addPathPatterns("/**");
        // 鉴权：默认全拦，仅 @PublicAccess 放行
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/error",
                        // 健康探针：公开访问，仅此一个 Actuator 端点（其余未暴露）
                        "/actuator/health",
                        "/actuator/health/**"
                );
        // 限流：识别 @RateLimit 按 IP 单日计数，超限 429（注册在鉴权之后）
        registry.addInterceptor(new RateLimitInterceptor(rateLimitService))
                .addPathPatterns("/**");
    }

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
