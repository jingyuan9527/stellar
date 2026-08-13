package com.stellar.ai.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回复反馈 + 关联消息快照（bad case 复盘原料）：👎 的消息连同回答内容与 RAG 引用一起展示。
 */
public record RagFeedbackVO(
        Long id,
        Long messageId,
        Integer value,
        String comment,
        String subjectType,
        String subjectId,
        String content,
        List<RagSource> refs,
        LocalDateTime createTime
) {
}