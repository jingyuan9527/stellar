package com.stellar.dto;

import lombok.Data;

/**
 * 神奇海螺预设回答分页查询
 */
@Data
public class ConchAnswerQueryDTO {

    private String answerText;

    private Integer enabled;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
