package com.stellar.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.infra.SseEmitterManager;
import com.stellar.interceptor.WebUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 任务通知 SSE 端点。对游客开放，按 subject（account:userId / ip:ip）订阅。
 * <p>前端建立长连接后，后端任务完成时通过 Redis pub/sub 广播 → 本实例推送通知。
 */
@Slf4j
@RestController
@RequestMapping("/ai/notify")
@RequiredArgsConstructor
public class AiNotifyController {

    private final SseEmitterManager sseEmitterManager;

    @PublicAccess
    @GetMapping("")
    public SseEmitter subscribe() {
        String subject = resolveSubject();
        SseEmitter emitter = sseEmitterManager.register(subject);
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("subject", subject), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // 降级：连接握手即失败（客户端已断开），completeWithError 触发清理，但需留排查痕迹
            log.warn("[AI通知] SSE connected 事件发送失败 subject={}: {}", subject, e.getMessage(), e);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private String resolveSubject() {
        if (StpUtil.isLogin()) {
            return "account:" + StpUtil.getLoginIdAsString();
        }
        return "ip:" + WebUtils.getClientIp();
    }
}
