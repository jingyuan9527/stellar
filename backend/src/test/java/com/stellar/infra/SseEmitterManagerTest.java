package com.stellar.infra;

import com.stellar.test.ReflectUtil;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SseEmitterManager} 单测：register 注册回调、send 空集合短路 / 正常推送 / 推送失败移除、
 * heartbeat 心跳与死连接清理。emitters 私有字段用 {@code ReflectUtil} 注入 mock 连接。
 */
class SseEmitterManagerTest {

    private SseEmitterManager manager() {
        return new SseEmitterManager();
    }

    @Test
    void register_返回emitter并注册回调() {
        SseEmitterManager mgr = manager();
        SseEmitter emitter = mgr.register("account:1");
        assertNotNull(emitter);
    }

    @Test
    void send_无连接_直接返回() {
        SseEmitterManager mgr = manager();
        mgr.send("account:1", "task", "data");
    }

    @Test
    void send_有连接_推送成功() throws Exception {
        SseEmitterManager mgr = manager();
        SseEmitter emitter = mock(SseEmitter.class);
        inject(mgr, "account:1", emitter);

        mgr.send("account:1", "task", "data");

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void send_推送失败_移除连接() throws Exception {
        SseEmitterManager mgr = manager();
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IllegalStateException("closed")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        inject(mgr, "account:1", emitter);

        mgr.send("account:1", "task", "data");

        // 连接被移除后第二次 send 短路
        mgr.send("account:1", "task", "data");
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void heartbeat_无连接_直接返回() {
        SseEmitterManager mgr = manager();
        mgr.heartbeat();
    }

    @Test
    void heartbeat_有连接_发ping_失败清理() throws Exception {
        SseEmitterManager mgr = manager();
        SseEmitter ok = mock(SseEmitter.class);
        SseEmitter dead = mock(SseEmitter.class);
        doThrow(new IllegalStateException("closed")).when(dead).send(any(SseEmitter.SseEventBuilder.class));
        inject(mgr, "account:1", ok, dead);

        mgr.heartbeat();

        verify(ok).send(any(SseEmitter.SseEventBuilder.class));
        // dead 只被发送一次，随后移除，再心跳只剩 ok
        mgr.heartbeat();
        verify(dead, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    private void inject(SseEmitterManager mgr, String subject, SseEmitter... emitters) {
        ConcurrentHashMap<String, CopyOnWriteArraySet<SseEmitter>> map =
                new ConcurrentHashMap<>();
        CopyOnWriteArraySet<SseEmitter> set = new CopyOnWriteArraySet<>();
        for (SseEmitter e : emitters) {
            set.add(e);
        }
        map.put(subject, set);
        ReflectUtil.setFinalField(mgr, "emitters", map);
    }
}
