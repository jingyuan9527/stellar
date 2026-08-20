package com.stellar.infra;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import com.stellar.ai.service.AiNotifyListener;

/**
 * SSE 连接管理器：按 subject（account:userId / ip:ip）分组管理 emitter，
 * 支持推送任务完成通知 + 定时心跳保活。
 * <p>多实例部署时，由 AiNotifyListener 收到 Redis pub/sub 消息后调 send 推送给本实例的连接。
 */
@Slf4j
@Component
public class SseEmitterManager {

    /** SSE 兜底超时 1h（P8 收紧自 24h）：长连接资源占用有界，30s 心跳保活下实际可长期存活 */
    private static final long EMITTER_TIMEOUT = 3600_000L;

    private final ConcurrentHashMap<String, CopyOnWriteArraySet<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 注册一个 SSE 连接，绑定到指定 subject。
     */
    public SseEmitter register(String subject) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        CopyOnWriteArraySet<SseEmitter> set = emitters.computeIfAbsent(subject, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);

        emitter.onCompletion(() -> remove(subject, emitter, set, "完成"));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(subject, emitter, set, "超时");
        });
        emitter.onError(e -> remove(subject, emitter, set, "异常:" + e.getMessage()));

        log.info("[SSE] 连接注册 subject={} 当前连接数={}", subject, set.size());
        return emitter;
    }

    /**
     * 向指定 subject 的所有连接推送事件。
     */
    public void send(String subject, String eventName, Object data) {
        Set<SseEmitter> set = emitters.get(subject);
        if (set == null || set.isEmpty()) return;
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                set.remove(emitter);
                log.debug("[SSE] 推送失败移除 subject={} err={}", subject, e.getMessage());
            }
        }
        if (set.isEmpty()) {
            emitters.remove(subject);
        }
    }

    /**
     * 心跳：每 30 秒向所有连接发送 ping 事件，防止代理空闲断连 + 检测死连接。
     */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        if (emitters.isEmpty()) return;
        int total = 0;
        for (var entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("", MediaType.TEXT_PLAIN));
                    total++;
                } catch (Exception e) {
                    entry.getValue().remove(emitter);
                }
            }
            if (entry.getValue().isEmpty()) {
                emitters.remove(entry.getKey());
            }
        }
        log.debug("[SSE] 心跳发送完成 活跃连接={}", total);
    }

    private void remove(String subject, SseEmitter emitter, Set<SseEmitter> set, String reason) {
        set.remove(emitter);
        if (set.isEmpty()) {
            emitters.remove(subject);
        }
        log.debug("[SSE] 连接移除 subject={} 原因={} 剩余={}", subject, reason, set.size());
    }
}
