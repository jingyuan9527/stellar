package com.stellar.interceptor;

import cn.dev33.satoken.stp.StpUtil;
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
        }
        return true;
    }

    /**
     * 判断目标方法或其所在类是否标注了 {@link PublicAccess}。
     */
    private boolean isPublicAccess(HandlerMethod handlerMethod) {
        return handlerMethod.getMethodAnnotation(PublicAccess.class) != null
                || handlerMethod.getBeanType().isAnnotationPresent(PublicAccess.class);
    }
}
