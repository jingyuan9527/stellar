package com.stellar.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 视频生成历史查询结果（不含 subjectType/subjectId）。
 */
@Data
public class AiVideoHistoryVO {

    private Long id;

    private String prompt;

    private String ratio;

    private Integer duration;

    /** generating / completed / failed */
    private String status;

    /** completed 时为 /file/{id}，其余为 null */
    private String url;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long durationMs;
}
