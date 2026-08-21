package com.stellar.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.ai.vo.AiNotifyMessage;
import com.stellar.ai.vo.AiVideoStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 视频生成轮询调度器：DB 驱动单调度线程。
 * <p>替代旧的"每任务独立 {@code @Async("aiTaskExecutor")} 线程 + Thread.sleep 轮询"——
 * 旧方案每个视频任务独占池线程最长 10 分钟，3 个并发任务即占满 aiTaskExecutor（maxPoolSize=4）
 * 饿死图片生成，池满时反压事件发布线程。新方案由唯一 {@link Scheduled} 线程每 5s 扫一轮
 * 待轮询视频任务逐条单次查询，不占业务线程池；节流靠每次 generating 轮询后推进 update_time。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiVideoTaskWorker {

    private final AiVideoService aiVideoService;
    private final AiTaskMapper aiTaskMapper;
    private final AiNotifyPublisher publisher;
    private final ObjectMapper objectMapper;

    private static final long POLL_INTERVAL_MS = 5_000;
    private static final long POLL_TIMEOUT_MS = 10 * 60 * 1_000;

    /**
     * 单调度线程扫描到期未轮询的视频任务，逐条单次查询供应商。
     * <p>@Scheduled 默认单线程调度器，本轮处理完才进入下一轮，天然串行不重叠。
     */
    @Scheduled(fixedRate = POLL_INTERVAL_MS)
    public void pollPendingVideos() {
        LocalDateTime lastPollBefore = LocalDateTime.now().minus(Duration.ofMillis(POLL_INTERVAL_MS));
        try {
            List<AiTask> pending = aiTaskMapper.selectPendingVideoTasks(lastPollBefore);
            if (pending.isEmpty()) {
                return;
            }
            log.debug("[AI视频] 调度扫描待轮询任务数={}", pending.size());
            for (AiTask task : pending) {
                pollOnce(task);
            }
        } catch (Exception e) {
            // 一次全扫失败不影响后续调度（下一轮仍会重新扫描）
            log.error("[AI视频] 调度扫描异常: {}", e.getMessage(), e);
        }
    }

    private void pollOnce(AiTask task) {
        Long taskId = task.getId();
        String videoId = extraText(task, "video_id");
        Long modelId = extraLong(task, "model_id");
        String subject = task.getSubjectType() + ":" + task.getSubjectId();
        if (videoId == null || modelId == null) {
            markTerminal(taskId, subject, videoId, "视频任务参数缺失(video_id/model_id)");
            log.warn("[AI视频] 任务参数缺失 taskId={} extra={}", taskId, task.getExtra());
            return;
        }
        LocalDateTime created = task.getCreateTime() != null ? task.getCreateTime() : task.getRequestTime();
        if (created != null && created.isBefore(LocalDateTime.now().minus(Duration.ofMillis(POLL_TIMEOUT_MS)))) {
            markTerminal(taskId, subject, videoId, "视频生成超时");
            return;
        }
        try {
            AiVideoStatusVO vo = aiVideoService.getTask(modelId, videoId);
            String status = vo.getStatus();
            if ("completed".equals(status)) {
                // getTask 内部已将本地行置 completed + file_id
                publisher.publish(new AiNotifyMessage(subject, "video", taskId, "completed"));
                log.info("[AI视频] 轮询完成 taskId={} videoId={}", taskId, videoId);
            } else if ("failed".equals(status)) {
                publisher.publish(new AiNotifyMessage(subject, "video", taskId, "failed"));
                log.info("[AI视频] 轮询失败 taskId={} videoId={}", taskId, videoId);
            } else {
                // 仍在生成：推进轮询时间戳，保证本任务下一轮才再查供应商
                touchUpdateTime(task);
            }
        } catch (Exception e) {
            // 供应商瞬态故障：错开一档下轮重试，不因单条异常中断整轮扫描
            log.warn("[AI视频] 轮询异常 taskId={}: {}", taskId, e.getMessage());
            touchUpdateTime(task);
        }
    }

    private void touchUpdateTime(AiTask task) {
        try {
            task.setUpdateTime(LocalDateTime.now());
            aiTaskMapper.updateById(task);
        } catch (Exception e) {
            log.error("[AI视频] 推进轮询时间戳失败 taskId={}: {}", task.getId(), e.getMessage());
        }
    }

    private void markTerminal(Long taskId, String subject, String videoId, String msg) {
        try {
            AiTask task = aiTaskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            task.setStatus("failed");
            if (msg != null) {
                task.setErrorMsg(msg);
            }
            task.setUpdateTime(LocalDateTime.now());
            aiTaskMapper.updateById(task);
            publisher.publish(new AiNotifyMessage(subject, "video", taskId, "failed"));
            log.warn("[AI视频] 标记失败 taskId={} videoId={} reason={}", taskId, videoId, msg);
        } catch (Exception e) {
            log.error("[AI视频] 标记失败状态异常 taskId={}: {}", taskId, e.getMessage());
        }
    }

    private String extraText(AiTask task, String key) {
        if (task.getExtra() == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(task.getExtra());
            return node.has(key) && !node.get(key).isNull() ? node.get(key).asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long extraLong(AiTask task, String key) {
        String s = extraText(task, key);
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}