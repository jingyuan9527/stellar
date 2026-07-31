package com.stellar.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型视图，带供应商名称便于前端展示。
 */
@Data
public class AiModelVO {

    private Long id;

    private Long providerId;

    private String providerName;

    private String model;

    private String modelType;

    private Integer enabled;

    private Integer isDefault;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
