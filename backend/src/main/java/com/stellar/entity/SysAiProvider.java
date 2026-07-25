package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 供应商：一组 endpoint+apiKey，下挂多个模型。
 */
@Data
@TableName("sys_ai_provider")
public class SysAiProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String endpoint;

    /** 返回前端时脱敏 */
    private String apiKey;

    /** 最近拉取到的可用模型列表，逗号分隔，重新拉取时覆盖 */
    private String availableModels;

    /** 0禁用 1启用 */
    private Integer enabled;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
