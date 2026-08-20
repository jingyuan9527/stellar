package com.stellar.common;

/**
 * 安全相关常量：登录会话内的标记 key 等。
 * <p>S3 真·强制改密：登录后若 {@code must_change_password=1}，在 Sa-Token 会话写入本 key，
 * AuthInterceptor 据此拦截除改密/登出/取用户信息外的全部受保护接口，杜绝非配合客户端
 * 凭默认口令访问业务功能。改密成功后由 UserService 同步清除。
 */
public final class SecurityConstants {

    /** Sa-Token 会话内「首次登录待强制改密」标记，Boolean.TRUE 表示当前会话须先改密 */
    public static final String SESSION_KEY_MUST_CHANGE_PASSWORD = "mustChangePassword";

    /** 强制改密状态下仍放行的接口路径（其余受保护接口一律拦截） */
    public static final String[] FORCED_CHANGE_PASSWORD_ALLOWED_PATHS = {
            "/user/change-password",
            "/user/info",
            "/auth/logout",
    };

    private SecurityConstants() {
    }
}