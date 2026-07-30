package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.entity.SysLog;
import com.stellar.entity.SysUser;
import com.stellar.interceptor.WebUtils;
import com.stellar.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 外部接口调用日志：统一记录 AI LLM / 图片 / 视频 / TTS / Embedding 等第三方接口调用结果。
 * <p>复用 sys_log 表（module=外部调用，operationType=OTHER），异步落库；
 * 同时输出运行日志，便于按 traceId 排查。异步线程无 web 上下文时 operator/ip/url 降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalCallLogger {

    private final SysLogService sysLogService;
    private final SysUserMapper sysUserMapper;

    private static final int MAX_PARAMS = 2000;
    private static final int MAX_ERROR = 2000;

    public void success(String provider, String action, String params, long durationMs) {
        record(provider, action, params, true, null, durationMs, null);
    }

    public void failure(String provider, String action, String params, String errorMsg, long durationMs) {
        record(provider, action, params, false, errorMsg, durationMs, null);
    }

    /** 异步线程调用时传入同步阶段捕获的 operator，避免请求上下文丢失导致 operator 降级。 */
    public void success(String provider, String action, String params, long durationMs, String operator) {
        record(provider, action, params, true, null, durationMs, operator);
    }

    public void failure(String provider, String action, String params, String errorMsg, long durationMs, String operator) {
        record(provider, action, params, false, errorMsg, durationMs, operator);
    }

    private void record(String provider, String action, String params,
                        boolean success, String errorMsg, long durationMs, String operator) {
        if (success) {
            log.info("[外部调用] 成功 provider={} action={} params={} durationMs={}", provider, action, params, durationMs);
        } else {
            log.warn("[外部调用] 失败 provider={} action={} params={} durationMs={} error={}",
                    provider, action, params, durationMs, errorMsg);
        }

        try {
            SysLog sysLog = new SysLog();
            sysLog.setModule("外部调用");
            sysLog.setOperationType("OTHER");
            sysLog.setOperator(operator != null ? operator : resolveOperator());
            sysLog.setRequestMethod("POST");
            sysLog.setRequestUrl(provider + " / " + action);
            sysLog.setJavaMethod(provider);
            sysLog.setParams(truncate(params, MAX_PARAMS));
            sysLog.setStatus(success ? 1 : 0);
            sysLog.setErrorMsg(truncate(errorMsg, MAX_ERROR));
            sysLog.setIp(WebUtils.getClientIp());
            sysLog.setDuration(durationMs);
            sysLog.setCreateTime(LocalDateTime.now());
            sysLogService.saveLog(sysLog);
        } catch (Exception e) {
            log.warn("[外部调用] 写 sys_log 失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private String resolveOperator() {
        try {
            if (!StpUtil.isLogin()) {
                return "anonymous";
            }
            Long userId = StpUtil.getLoginIdAsLong();
            SysUser user = sysUserMapper.selectById(userId);
            return user != null ? user.getUsername() : "user:" + userId;
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
