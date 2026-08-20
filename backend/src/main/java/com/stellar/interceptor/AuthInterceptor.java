package com.stellar.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.common.BusinessException;
import com.stellar.common.ResultCode;
import com.stellar.common.SecurityConstants;
import com.stellar.common.annotation.PublicAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 统一鉴权拦截器，替代 Sa-Token 默认全拦策略。
 *
 * <ul>
 *   <li>命中 {@link PublicAccess}（方法或类级）：游客与登录用户均放行，跳过登录校验。</li>
 *   <li>未命中：要求 Sa-Token 登录，未登录抛 {@code NotLoginException}，
 *       由 {@code GlobalExceptionHandler} 转为统一 401 envelope。</li>
 *   <li>已登录但会话带「强制改密」标记（S3：默认口令首登未改密）：除改密/登出/取用户信息外
 *       其余受保护接口一律 403 拦截，防止非配合客户端凭默认口令访问业务功能。</li>
 *   <li>非 Controller 方法（静态资源等）：直接放行，交由后续过滤器/处理器处理。</li>
 * </ul>
 *
 * <p>放行的公开接口仍应在方法内判断登录态并叠加 IP 限流（阶段3），避免裸奔。
 *
 * @author stellar
 */
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 只对 Controller 方法做鉴权；静态资源/错误处理器等直接放行
        if (handler instanceof HandlerMethod handlerMethod) {
            if (isPublicAccess(handlerMethod)) {
                log.debug("[鉴权] 公开访问放行: {}#{}",
                        handlerMethod.getBeanType().getSimpleName(),
                        handlerMethod.getMethod().getName());
                return true;
            }
            // 其余接口要求登录；未登录抛 NotLoginException，由全局异常处理器统一处理
            StpUtil.checkLogin();
            // S3 真·强制改密：会话标记未清前，仅放行改密/登出/取用户信息，其余受保护接口一律 403
            if (Boolean.TRUE.equals(StpUtil.getSession().get(SecurityConstants.SESSION_KEY_MUST_CHANGE_PASSWORD))
                    && !isAllowedDuringForcedChange(request.getRequestURI())) {
                log.warn("[鉴权] 强制改密未完成，拦截受保护接口: {} {}", request.getMethod(), request.getRequestURI());
                throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "首次登录需先修改默认密码");
            }
        }
        return true;
    }

    /** 强制改密状态下仍允许访问的接口路径（改密/取当前用户/登出），其余受保护接口一律拦截 */
    private boolean isAllowedDuringForcedChange(String uri) {
        for (String path : SecurityConstants.FORCED_CHANGE_PASSWORD_ALLOWED_PATHS) {
            if (path.equals(uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断目标方法或其所在类是否标注了 {@link PublicAccess}。
     */
    private boolean isPublicAccess(HandlerMethod handlerMethod) {
        return handlerMethod.getMethodAnnotation(PublicAccess.class) != null
                || handlerMethod.getBeanType().isAnnotationPresent(PublicAccess.class);
    }
}
