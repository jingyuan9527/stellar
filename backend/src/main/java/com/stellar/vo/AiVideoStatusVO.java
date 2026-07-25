package com.stellar.vo;

import lombok.Data;

/**
 * AI 视频任务状态查询结果。
 */
@Data
public class AiVideoStatusVO {

    /** queued / in_progress / completed / failed */
    private String status;

    private Integer progress;

    /** completed 时为 /file/{id}，其余为 null */
    private String videoUrl;

    private String seconds;

    private String size;
}
