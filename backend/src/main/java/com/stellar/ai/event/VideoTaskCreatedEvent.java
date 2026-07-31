package com.stellar.ai.event;

import com.stellar.ai.service.AiVideoService;
import com.stellar.ai.service.AiVideoTaskWorker;

/**
 * 视频任务创建事件：AiVideoService.createTask 发布，AiVideoTaskWorker 监听后开始轮询供应商。
 */
public record VideoTaskCreatedEvent(
        Long taskId,
        Long modelId,
        String videoId
) {
}
