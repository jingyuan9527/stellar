package com.stellar.config;

import com.stellar.interceptor.WebUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 安全相关启动配置：向 {@link WebUtils} 注入可信代理白名单。
 * <p>独立成类是因为 WebUtils 是静态工具，需在容器启动期完成一次性装配。
 */
@Configuration
public class TrustedProxyConfig {

    public TrustedProxyConfig(
            @Value("${stellar.security.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}") String trustedProxies) {
        WebUtils.configureTrustedProxies(trustedProxies);
    }
}
