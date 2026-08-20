package com.stellar.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.annotation.Log;
import com.stellar.interceptor.WebUtils;
import com.stellar.system.entity.SysLog;
import com.stellar.enums.OperationType;
import com.stellar.system.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
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
    private final ObjectMapper objectMapper;

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(\"(?:password|oldPassword|confirmPassword|token|secretKey|apiKey)\"\\s*:\\s*)\"[^\"]*\"",
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
            sysLog.setIp(WebUtils.getClientIp(request));
        }

        String className = joinPoint.getTarget().getClass().getName();
        String methodName = joinPoint.getSignature().getName();
        sysLog.setJavaMethod(className + "." + methodName);
        String[] paramNames = null;
        if (joinPoint.getSignature() instanceof MethodSignature ms) {
            paramNames = ms.getParameterNames();
        }
        sysLog.setParams(maskParams(joinPoint.getArgs(), paramNames));
        resolveOperator(sysLog, logAnno.type(), joinPoint.getArgs());

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
            // 查询类成功不落库（噪音），但查询报错（NPE/数据异常等）仍记录，便于第一时间发现 bug
            boolean skip = logAnno.type() == OperationType.QUERY && sysLog.getStatus() != null && sysLog.getStatus() == 1;
            if (!skip) {
                try {
                    sysLogService.saveLog(sysLog);
                } catch (Exception ex) {
                    log.error("保存操作日志失败: {}", ex.getMessage(), ex);
                }
            }
        }
    }

    /**
     * 解析操作人：LOGIN 取请求体 username；其余类型仅取 userId（查用户名挪到 saveLog 异步线程，
     * 消除请求线程上的同步 SELECT）。未登录记 anonymous。
     */
    private void resolveOperator(SysLog sysLog, OperationType type, Object[] args) {
        if (type == OperationType.LOGIN) {
            sysLog.setOperator(resolveLoginOperator(args));
            return;
        }
        try {
            if (StpUtil.isLogin()) {
                sysLog.setOperatorUserId(StpUtil.getLoginIdAsLong());
            } else {
                sysLog.setOperator("anonymous");
            }
        } catch (Exception e) {
            sysLog.setOperator("anonymous");
        }
    }

    private String resolveLoginOperator(Object[] args) {
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

    /**
     * 序列化方法参数为 JSON 并脱敏。按参数名组装成对象（而非数组），
     * 使正则能按字段名命中 @RequestParam 的 apiKey 等敏感参数；
     * @RequestBody 的 DTO 字段名同样会被命中。
     */
    private String maskParams(Object[] args, String[] paramNames) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null) continue;
                if (arg instanceof HttpServletRequest
                        || arg instanceof jakarta.servlet.http.HttpServletResponse
                        || arg instanceof jakarta.servlet.http.HttpSession
                        || arg instanceof org.springframework.web.multipart.MultipartFile) {
                    continue;
                }
                String name = (paramNames != null && i < paramNames.length && paramNames[i] != null)
                        ? paramNames[i] : "arg" + i;
                node.putPOJO(name, arg);
            }
            String json = objectMapper.writeValueAsString(node);
            json = SENSITIVE_PATTERN.matcher(json).replaceAll("$1\"******\"");
            return truncate(json, MAX_FIELD_LENGTH);
        } catch (Exception e) {
            return "序列化失败: " + truncate(e.getMessage(), 200);
        }
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
}
