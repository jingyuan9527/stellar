package com.stellar.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.annotation.Log;
import com.stellar.entity.SysLog;
import com.stellar.entity.SysUser;
import com.stellar.enums.OperationType;
import com.stellar.mapper.SysUserMapper;
import com.stellar.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 操作日志切面：拦截 {@link Log} 注解标注的 Controller 方法，
 * 统一采集操作信息并异步写入 sys_log 表。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LogAspect {

    private final SysLogService sysLogService;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(\"(?:password|oldPassword|confirmPassword|token|secretKey)\"\\s*:\\s*)\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE);

    private static final int MAX_FIELD_LENGTH = 2000;

    @Around("@annotation(logAnno)")
    public Object around(ProceedingJoinPoint joinPoint, Log logAnno) throws Throwable {
        long start = System.currentTimeMillis();
        SysLog sysLog = new SysLog();
        sysLog.setModule(logAnno.title());
        sysLog.setOperationType(logAnno.type().name());

        HttpServletRequest request = getRequest();
        if (request != null) {
            sysLog.setRequestMethod(request.getMethod());
            sysLog.setRequestUrl(request.getRequestURI());
            sysLog.setIp(getIp(request));
        }

        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        sysLog.setJavaMethod(className + "." + methodName);
        sysLog.setParams(maskParams(joinPoint.getArgs()));
        sysLog.setOperator(resolveOperator(logAnno.type(), joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            sysLog.setStatus(1);
            return result;
        } catch (Throwable e) {
            sysLog.setStatus(0);
            sysLog.setErrorMsg(truncate(stackSummary(e), MAX_FIELD_LENGTH));
            throw e;
        } finally {
            sysLog.setDuration(System.currentTimeMillis() - start);
            sysLog.setCreateTime(LocalDateTime.now());
            try {
                sysLogService.saveLog(sysLog);
            } catch (Exception ex) {
                log.error("保存操作日志失败: {}", ex.getMessage(), ex);
            }
        }
    }

    private String resolveOperator(OperationType type, Object[] args) {
        if (type == OperationType.LOGIN) {
            for (Object arg : args) {
                if (arg == null) continue;
                try {
                    JsonNode node = objectMapper.valueToTree(arg);
                    JsonNode username = node.get("username");
                    if (username != null && username.isTextual()) {
                        return username.asText();
                    }
                } catch (Exception ignored) {
                }
            }
            return "unknown";
        }
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            SysUser user = sysUserMapper.selectById(userId);
            return user != null ? user.getUsername() : "user:" + userId;
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private String maskParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(args);
            json = SENSITIVE_PATTERN.matcher(json).replaceAll("$1\"******\"");
            return truncate(json, MAX_FIELD_LENGTH);
        } catch (Exception e) {
            return "序列化失败: " + truncate(e.getMessage(), 200);
        }
    }

    private String getIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isBlank(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private HttpServletRequest getRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String stackSummary(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage());
        StackTraceElement[] trace = e.getStackTrace();
        for (int i = 0; i < trace.length && i < 5; i++) {
            sb.append("\n    at ").append(trace[i]);
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
