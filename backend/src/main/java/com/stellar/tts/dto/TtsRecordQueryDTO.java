package com.stellar.tts.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音合成记录分页查询参数
 */
@Data
public class TtsRecordQueryDTO {

    /** 文本关键词（模糊匹配） */
    private String text;

    /** 发音人 */
    private String voice;

    /** 起始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "单页最多 100 条")
    private Integer pageSize = 10;
}
