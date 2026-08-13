package com.stellar.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 聊天回复反馈（👍有用 / 👎没用）：数据飞轮评估集原料，期4 复盘 bad case + 回归用。
 */
@Data
@TableName("rag_feedback")
public class RagFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被评价消息ID(引用 ai_chat_message.id) */
    private Long messageId;

    /** 评价: 1有用 -1没用 */
    private Integer value;

    /** 用户可选补充说明 */
    private String comment;

    /** 主体类型: account/ip */
    private String subjectType;

    /** 主体ID: userId 或 IP */
    private String subjectId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
