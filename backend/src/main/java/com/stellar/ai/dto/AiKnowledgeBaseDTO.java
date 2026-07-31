package com.stellar.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiKnowledgeBaseDTO {

    private Long id;

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    /** 向量化模型ID(EMBEDDING类型)，为空则用 EMBEDDING 默认模型 */
    private Long embeddingModelId;
}
