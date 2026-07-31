package com.stellar.infra;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;

public final class SubjectUtils {

    private SubjectUtils() {}

    public static String subjectType() {
        return StpUtil.isLogin() ? "account" : "ip";
    }

    public static String subjectId(HttpServletRequest request) {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : getClientIp(request);
    }

    public static String subjectKey(HttpServletRequest request) {
        return subjectType() + ":" + subjectId(request);
    }

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
