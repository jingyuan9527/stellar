package com.stellar.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型：归属某供应商，带类型标签，按类型设默认。
 */
@Data
@TableName("sys_ai_model")
public class SysAiModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long providerId;

    private String model;

    /** 模型类型: TEXT/IMAGE/AUDIO/EMBEDDING/VIDEO（字典 model_type） */
    private String modelType;

    /** 0禁用 1启用 */
    private Integer enabled;

    /** 该类型下默认: 0否 1是（同类型互斥） */
    private Integer isDefault;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
