package com.stellar.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 人设：预设 system prompt，聊天时快捷选择注入 LLM。
 */
@Data
@TableName("ai_persona")
public class AiPersona {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String systemPrompt;

    private String description;

    private Integer enabled;

    private Integer sortOrder;

    private Integer builtIn;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
