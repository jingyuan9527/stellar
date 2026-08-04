package com.stellar.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * AI 模型批量操作 DTO：批量启停用 enabled；批量删除忽略该字段。
 */
@Data
public class AiModelBatchDTO {

    @NotEmpty(message = "模型 id 列表不能为空")
    private List<Long> ids;

    private Integer enabled;
}