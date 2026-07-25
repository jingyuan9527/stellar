package com.stellar.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 文本生成历史记录查询结果（不含 subjectType/subjectId，避免泄露主体信息）。
 */
@Data
public class AiChatRecordVO {

    private Long id;

    private String model;

    /** 实际发送给 LLM 的完整 prompt */
    private String prompt;

    /** 流式完整文本 */
    private String result;

    /** success / failed */
    private String status;

    private String errorMsg;

    private LocalDateTime requestTime;

    private LocalDateTime responseTime;

    private Long durationMs;

    private LocalDateTime createTime;
}
