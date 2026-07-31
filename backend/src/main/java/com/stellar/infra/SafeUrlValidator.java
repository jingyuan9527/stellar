package com.stellar.infra;

import com.stellar.common.BusinessException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

public final class SafeUrlValidator {

    private SafeUrlValidator() {
    }

    public static URI validatePublicHttpUrl(String rawUrl, String purpose) {
        final URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (Exception e) {
            throw new BusinessException(purpose + " URL 格式无效");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || host == null || uri.getUserInfo() != null) {
            throw new BusinessException(purpose + " 仅允许无用户信息的 http/https URL");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            throw new BusinessException(purpose + " 禁止访问本机地址");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new BusinessException(purpose + " 域名未解析到地址");
            }
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) {
                    throw new BusinessException(purpose + " 禁止访问本机或私网地址");
                }
            }
        } catch (UnknownHostException e) {
            throw new BusinessException(purpose + " 域名解析失败");
        }
        return uri;
    }

    public static String normalizePublicBaseUrl(String rawUrl, String purpose) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessException(purpose + " 不能为空");
        }
        String normalized = rawUrl.trim().replaceAll("/+$", "");
        URI uri = validatePublicHttpUrl(normalized, purpose);
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new BusinessException(purpose + " 基础 URL 不能包含查询参数或片段");
        }
        return normalized;
    }

    public static byte[] readLimited(InputStream input, long maxBytes, String purpose) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new BusinessException(purpose + " 超过大小限制 " + maxBytes + " 字节");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0)
                    && !(first == 198 && (second == 18 || second == 19))
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            if ((first & 0xfe) == 0xfc) {
                return false;
            }
            boolean ipv4Mapped = true;
            for (int i = 0; i < 10; i++) {
                ipv4Mapped &= bytes[i] == 0;
            }
            ipv4Mapped &= bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
            if (ipv4Mapped) {
                int v4First = bytes[12] & 0xff;
                int v4Second = bytes[13] & 0xff;
                return v4First != 0
                        && v4First != 10
                        && v4First != 127
                        && !(v4First == 100 && v4Second >= 64 && v4Second <= 127)
                        && !(v4First == 169 && v4Second == 254)
                        && !(v4First == 172 && v4Second >= 16 && v4Second <= 31)
                        && !(v4First == 192 && (v4Second == 0 || v4Second == 168))
                        && !(v4First == 198 && (v4Second == 18 || v4Second == 19))
                        && v4First < 224;
            }
            return true;
        }
        return false;
    }
}
