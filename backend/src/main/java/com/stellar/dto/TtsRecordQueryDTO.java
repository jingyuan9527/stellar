package com.stellar.dto;

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

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
