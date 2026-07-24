package com.stellar.config;

import com.stellar.interceptor.AuthInterceptor;
import com.stellar.interceptor.RateLimitInterceptor;
import com.stellar.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final RateLimitService rateLimitService;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * 鉴权拦截器：默认所有接口要求登录，仅标注 {@link com.stellar.common.annotation.PublicAccess}
     * 的方法/类对游客放行。放行的公开接口仍应在方法内用 {@code StpUtil.isLogin()} 区分游客，
     * 并叠加 IP 限流（阶段3），避免裸奔。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 鉴权：默认全拦，仅 @PublicAccess 放行
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/login",
                        "/auth/register",
                        "/error",
                        "/uploads/**"
                );
        // 限流：识别 @RateLimit 按 IP 单日计数，超限 429（注册在鉴权之后）
        registry.addInterceptor(new RateLimitInterceptor(rateLimitService))
                .addPathPatterns("/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 本地上传文件静态映射：/uploads/** → 磁盘目录；游客可读（拦截器已 excludePathPatterns 放行）
        String dir = Paths.get(uploadDir).toAbsolutePath().normalize()
                .toString().replace("\\", "/");
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + dir + "/");
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
