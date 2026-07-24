package com.stellar.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 神奇海螺提问历史展示对象
 */
@Data
public class ConchRecordVO {

    private Long id;

    private String questionText;

    private Long answerId;

    /** 命中预设的回答文本（关联查出，便于后台展示） */
    private String answerText;

    private Long userId;

    private LocalDateTime createTime;
}
