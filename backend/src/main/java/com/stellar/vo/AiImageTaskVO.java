package com.stellar.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 图片生成任务状态查询结果。
 */
@Data
public class AiImageTaskVO {

    private Long taskId;

    /** generating / completed / failed */
    private String status;

    private String prompt;

    /** completed 时为 /file/{id}，其余为 null */
    private String url;

    private String errorMsg;

    private LocalDateTime createTime;
}
