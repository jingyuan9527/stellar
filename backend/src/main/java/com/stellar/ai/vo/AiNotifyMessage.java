package com.stellar.ai.vo;

/**
 * AI 任务完成通知消息：Redis pub/sub 广播 + SSE 推送给前端。
 * subject = subjectType:subjectId（account:123 / ip:1.2.3.4）
 */
public record AiNotifyMessage(
        String subject,
        String type,
        Long taskId,
        String status
) {
}
