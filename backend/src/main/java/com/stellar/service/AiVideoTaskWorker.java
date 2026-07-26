package com.stellar.service;

import com.stellar.entity.SysAiVideoTask;
import com.stellar.event.VideoTaskCreatedEvent;
import com.stellar.mapper.SysAiVideoTaskMapper;
import com.stellar.vo.AiNotifyMessage;
import com.stellar.vo.AiVideoStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI 视频生成异步 worker：监听 VideoTaskCreatedEvent，@Async 轮询供应商状态，
 * completed/failed 时发 SSE 通知。复用 AiVideoService.getTask 的查+下载+更新逻辑。
 * <p>与 AiVideoService 用事件解耦，避免循环依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiVideoTaskWorker {

    private final AiVideoService aiVideoService;
    private final SysAiVideoTaskMapper videoTaskMapper;
    private final AiNotifyPublisher publisher;

    private static final long POLL_INTERVAL_MS = 5_000;
    private static final long POLL_TIMEOUT_MS = 10 * 60 * 1_000;

    @Async("aiTaskExecutor")
    @EventListener
    public void onVideoTaskCreated(VideoTaskCreatedEvent event) {
        pollVideoStatus(event.taskId(), event.modelId(), event.videoId());
    }

    private void pollVideoStatus(Long taskId, Long modelId, String videoId) {
        SysAiVideoTask task = videoTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("[AI视频] 轮询任务不存在 taskId={}", taskId);
            return;
        }
        String subject = task.getSubjectType() + ":" + task.getSubjectId();
        long start = System.currentTimeMillis();
        log.info("[AI视频] 开始轮询 taskId={} videoId={}", taskId, videoId);

        while (System.currentTimeMillis() - start < POLL_TIMEOUT_MS) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[AI视频] 轮询被中断 taskId={}", taskId);
                return;
            }
            try {
                AiVideoStatusVO vo = aiVideoService.getTask(modelId, videoId);
                String status = vo.getStatus();
                if ("completed".equals(status)) {
                    publisher.publish(new AiNotifyMessage(subject, "video", taskId, "completed"));
                    log.info("[AI视频] 轮询完成 taskId={} videoId={}", taskId, videoId);
                    return;
                } else if ("failed".equals(status)) {
                    publisher.publish(new AiNotifyMessage(subject, "video", taskId, "failed"));
                    log.info("[AI视频] 轮询失败 taskId={} videoId={}", taskId, videoId);
                    return;
                }
            } catch (Exception e) {
                log.warn("[AI视频] 轮询异常 taskId={}: {}", taskId, e.getMessage());
            }
        }

        markTimeout(taskId, subject, videoId);
    }

    private void markTimeout(Long taskId, String subject, String videoId) {
        try {
            SysAiVideoTask task = videoTaskMapper.selectById(taskId);
            if (task == null) return;
            task.setStatus("failed");
            task.setErrorMsg("视频生成超时");
            task.setUpdateTime(LocalDateTime.now());
            videoTaskMapper.updateById(task);
            publisher.publish(new AiNotifyMessage(subject, "video", taskId, "failed"));
            log.warn("[AI视频] 轮询超时 taskId={} videoId={}", taskId, videoId);
        } catch (Exception e) {
            log.error("[AI视频] 标记超时失败 taskId={}: {}", taskId, e.getMessage());
        }
    }
}
