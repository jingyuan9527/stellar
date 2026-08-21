package com.stellar.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SSE 发送通道：独立写线程 + 有界队列，解耦慢客户端 socket 写阻塞与 HTTP 响应读取线程
 * （慢客户端会占满 HttpClient worker 拖慢所有 AI 请求）。
 * <p>内容分片与终端 done 都经同一单线程队列串行写出，保证先后顺序。
 * 队列满时提交方按 CallerRunsPolicy 内联执行（背压：限速读取上游，防内存无界增长）。
 * 分片发送失败置 disconnected 标记，生产者下次 send 抛 {@link ClientDisconnectedException} 提前终止读取。
 */
@Slf4j
public class SseEmitterChannel {

    private final SseEmitter emitter;
    private final ThreadPoolExecutor executor;
    private volatile boolean disconnected;

    public SseEmitterChannel(SseEmitter emitter) {
        this.emitter = emitter;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(512),
                r -> {
                    Thread t = new Thread(r, "sse-sender");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /** 发送事件（内容分片），客户端已断开则抛 {@link ClientDisconnectedException}。 */
    public void send(Map<String, Object> data) throws ClientDisconnectedException {
        if (disconnected) {
            throw new ClientDisconnectedException(new IOException("SSE 客户端已断开"));
        }
        try {
            executor.execute(() -> doSend(data));
        } catch (RejectedExecutionException e) {
            // 执行器已关闭（终态后仍被调用）→ 连接已结束，按客户端断开处理
            throw new ClientDisconnectedException(e);
        }
    }

    /** 发送终态事件并 complete emitter，保证排在所有已入队分片之后。 */
    public void sendAndComplete(Map<String, Object> data) {
        try {
            executor.execute(() -> {
                doSend(data);
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.warn("SSE complete 失败: {}", e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("SSE 终态发送被拒绝（连接可能已关闭）: {}", e.getMessage());
        }
    }

    private void doSend(Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            disconnected = true;   // 客户端断开/连接已完成 → 通知生产者提前终止读取
            log.debug("SSE 分片发送失败（视为客户端断开）: {}", e.getMessage());
        }
    }

    /** 关闭写线程：已入队任务（含终态）执行完，不再接受新任务。 */
    public void shutdown() {
        executor.shutdown();
    }

    /** 客户端断开/连接已完成的信号，读取线程据此中止，避免无意义读上游。 */
    public static final class ClientDisconnectedException extends IOException {
        private ClientDisconnectedException(Throwable cause) {
            super(cause.getMessage(), cause);
        }
    }
}