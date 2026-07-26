package com.stellar.event;

/**
 * 视频任务创建事件：AiVideoService.createTask 发布，AiVideoTaskWorker 监听后开始轮询供应商。
 */
public record VideoTaskCreatedEvent(
        Long taskId,
        Long modelId,
        String videoId
) {
}
