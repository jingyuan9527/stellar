package com.stellar.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 神奇海螺预设回答分页查询
 */
@Data
public class ConchAnswerQueryDTO {

    private String answerText;

    private Integer enabled;

    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "单页最多 100 条")
    private Integer pageSize = 10;
}
