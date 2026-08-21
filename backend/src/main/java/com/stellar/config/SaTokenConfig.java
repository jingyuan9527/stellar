package com.stellar.config;

import com.stellar.interceptor.AuthInterceptor;
import com.stellar.interceptor.RateLimitInterceptor;
import com.stellar.interceptor.RequestLogInterceptor;
import com.stellar.infra.RateLimitService;
import com.stellar.monitor.HttpRequestMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final RateLimitService rateLimitService;
    private final HttpRequestMetrics httpRequestMetrics;

    /** CORS 白名单（逗号分隔）。凭据型跨域严禁通配，启动时校验兜底 */
    @Value("${stellar.cors.allowed-origins:http://localhost:5173}")
    private String corsAllowedOrigins;

    /**
     * 鉴权拦截器：默认所有接口要求登录，仅标注 {@link com.stellar.common.annotation.PublicAccess}
     * 的方法/类对游客放行。放行的公开接口仍应在方法内用 {@code StpUtil.isLogin()} 区分游客，
     * 并叠加 IP 限流（阶段3），避免裸奔。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 请求日志 + traceId：最先执行（preHandle 设 traceId，afterCompletion 记耗时）
        registry.addInterceptor(new RequestLogInterceptor(httpRequestMetrics))
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
        // 限流：识别 @RateLimit，游客按 IP / 登录用户按 userId 单日计数，超限 429（注册在鉴权之后）
        registry.addInterceptor(new RateLimitInterceptor(rateLimitService))
                .addPathPatterns("/**");
    }

    /**
     * CORS 白名单：仅放行配置的前端源，白名单内保留凭据支持。
     * 不允许通配（allowCredentials + 通配 = 任意站点可借用户登录态跨域调用）。
     */
    @Bean
    public CorsFilter corsFilter() {
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (origins.isEmpty() || origins.contains("*")) {
            throw new IllegalStateException("stellar.cors.allowed-origins 必须为显式域名白名单，禁止空值或通配符");
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        origins.forEach(config::addAllowedOrigin);
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);
        log.info("[CORS] 白名单源: {}", origins);
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
