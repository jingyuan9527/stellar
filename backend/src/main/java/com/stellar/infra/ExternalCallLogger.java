package com.stellar.infra;

import com.stellar.interceptor.WebUtils;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 外部接口调用日志：统一记录 AI LLM / 图片 / 视频 / TTS / Embedding 等第三方接口调用结果。
 * <p>本类只负责请求上下文捕获（操作人/IP）与截断，落库走 {@link CallLogSink} 缝，
 * 由 system 模块提供实现写入 sys_log；同时输出运行日志，便于按 traceId 排查。异步线程无 web 上下文时 operator/ip 降级。
 * <p>operator 解析：同步阶段只取 userId（不查库），用户名由落库方异步线程解析。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalCallLogger {

    private final CallLogSink callLogSink;

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
            Long operatorUserId = null;
            if (operator == null) {
                // 只取登录 userId（不查库），用户名由落库方异步线程解析；未登录/异常记 anonymous
                try {
                    if (StpUtil.isLogin()) {
                        operatorUserId = StpUtil.getLoginIdAsLong();
                    } else {
                        operator = "anonymous";
                    }
                } catch (Exception e) {
                    operator = "anonymous";
                }
            }
            callLogSink.write(ExternalCallLogEntry.builder()
                    .provider(provider)
                    .action(action)
                    .params(truncate(params, MAX_PARAMS))
                    .success(success)
                    .errorMsg(truncate(errorMsg, MAX_ERROR))
                    .durationMs(durationMs)
                    .operator(operator)
                    .operatorUserId(operatorUserId)
                    .ip(WebUtils.getClientIp())
                    .build());
        } catch (Exception e) {
            log.warn("[外部调用] 写日志失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
