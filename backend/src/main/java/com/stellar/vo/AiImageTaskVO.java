package com.stellar.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 图片生成任务状态查询结果。
 * <p>单任务轮询与历史列表共用：历史列表额外填充 updateTime/durationMs/size/ratio。
 */
@Data
public class AiImageTaskVO {

    private Long taskId;

    /** generating / completed / failed */
    private String status;

    private String prompt;

    private String size;

    private String ratio;

    /** completed 时为 /file/{id}，其余为 null */
    private String url;

    private String errorMsg;

    /** 请求时间（任务创建时刻） */
    private LocalDateTime createTime;

    /** 返回时间（生成完成/失败时刻，轮询中可能为 null） */
    private LocalDateTime updateTime;

    /** 耗时（毫秒），updateTime - createTime */
    private Long durationMs;
}
