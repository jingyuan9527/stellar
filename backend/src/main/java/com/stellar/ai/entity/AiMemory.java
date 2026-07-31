package com.stellar.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 长期记忆：定期整理会话为事实陈述，按账号，对话时注入 system prompt。
 */
@Data
@TableName("ai_memory")
public class AiMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String content;

    private Long sourceSessionId;

    private LocalDateTime createTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
