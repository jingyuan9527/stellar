package com.stellar.vo;

import lombok.Data;

/**
 * AI 视频任务创建结果。
 */
@Data
public class AiVideoTaskVO {

    private String taskId;

    private String videoId;

    private String status;
}
