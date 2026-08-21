package com.stellar.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Web 层通用工具：客户端真实 IP 解析的<b>唯一实现</b>，
 * 拦截器/切面/Controller/Service 共用。
 *
 * <p>反代头（X-Forwarded-For/X-Real-IP）可被客户端任意伪造，仅当直连地址
 * （remoteAddr）命中可信代理白名单（{@code stellar.security.trusted-proxies}）时才采信；
 * 否则一律取 remoteAddr，防止伪造头绕过 IP 限流/风控。
 *
 * <p>解析优先级（仅可信代理来源）：X-Forwarded-For 首段（跳过 unknown）&gt; X-Real-IP &gt; 远端地址；
 * 无请求上下文（如异步线程）返回 {@code unknown}。
 */
@Slf4j
public final class WebUtils {

    private WebUtils() {
    }

    /** 可信代理列表：条目为精确 IP 或 IPv4 CIDR（如 10.0.0.0/8）。IPv6 仅支持精确匹配。 */
    private static volatile List<String> trustedProxies = List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    /**
     * 启动时注入可信代理配置（由 TrustedProxyConfig 调用），避免散落 @Value。
     */
    public static void configureTrustedProxies(String commaSeparated) {
        List<String> parsed = Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (parsed.isEmpty()) {
            throw new IllegalStateException("stellar.security.trusted-proxies 不能为空，至少保留本机回环");
        }
        trustedProxies = parsed;
        log.info("[IP解析] 可信代理白名单: {}", parsed);
    }

    /**
     * 从请求解析客户端 IP。request 为 null 时返回 {@code unknown}。
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = request.getRemoteAddr();
        // 不可信来源直连：代理头视为伪造，直接用直连地址
        if (!isTrustedProxy(remoteAddr)) {
            return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
        }
        String ip = firstNonUnknown(request.getHeader("X-Forwarded-For"));
        if (!StringUtils.hasText(ip)) {
            ip = firstNonUnknown(request.getHeader("X-Real-IP"));
        }
        if (!StringUtils.hasText(ip)) {
            ip = remoteAddr;
        }
        return StringUtils.hasText(ip) ? ip : "unknown";
    }

    private static boolean isTrustedProxy(String remoteAddr) {
        if (!StringUtils.hasText(remoteAddr)) {
            return false;
        }
        for (String entry : trustedProxies) {
            if (entry.contains("/") ? matchesIpv4Cidr(remoteAddr, entry) : entry.equalsIgnoreCase(remoteAddr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * IPv4 CIDR 匹配；任一侧非点分十进制 IPv4（如 IPv6）返回 false（IPv6 只能精确匹配）。
     */
    private static boolean matchesIpv4Cidr(String ip, String cidr) {
        int slash = cidr.indexOf('/');
        long ipVal = toIpv4Long(ip);
        long baseVal = toIpv4Long(cidr.substring(0, slash));
        if (ipVal < 0 || baseVal < 0) {
            return false;
        }
        int prefix;
        try {
            prefix = Integer.parseInt(cidr.substring(slash + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        if (prefix <= 0) {
            return true;
        }
        if (prefix >= 32) {
            return ipVal == baseVal;
        }
        long mask = (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        return (ipVal & mask) == (baseVal & mask);
    }

    /** 点分十进制转 long；非法格式返回 -1。 */
    private static long toIpv4Long(String s) {
        String[] parts = s.split("\\.");
        if (parts.length != 4) {
            return -1;
        }
        long val = 0;
        for (String part : parts) {
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return -1;
            }
            if (octet < 0 || octet > 255) {
                return -1;
            }
            val = (val << 8) | octet;
        }
        return val;
    }

    /**
     * 逗号分隔头取第一个非空白、非 unknown 的 IP（如 X-Forwarded-For: 1.2.3.4, 5.6.7.8）。
     * 代理链中的 unknown 占位需跳过，取第一个真实 IP。
     */
    private static String firstNonUnknown(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        for (String part : headerValue.split(",")) {
            String candidate = part.trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(candidate) && !"unknown".equals(candidate)) {
                return part.trim();
            }
        }
        return null;
    }

    /**
     * 从当前请求上下文解析客户端 IP（供 Service 层无 HttpServletRequest 入参处使用）。
     * 异步线程中请求上下文可能已失效，此时返回 {@code unknown}，调用方应降级处理。
     */
    public static String getClientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }
            return getClientIp(attributes.getRequest());
        } catch (Exception e) {
            return "unknown";
        }
    }
}
