package com.stellar.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 神奇海螺预设回答展示对象
 */
@Data
public class ConchAnswerVO {

    private Long id;

    private String answerText;

    private String matchDescription;

    private Long fileId;

    private Integer enabled;

    private Integer sortOrder;

    private LocalDateTime createTime;
}
