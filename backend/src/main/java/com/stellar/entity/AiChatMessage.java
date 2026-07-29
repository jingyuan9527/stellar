package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 聊天消息：会话内逐条 system/user/assistant 消息。
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** system / user / assistant */
    private String role;

    private String content;

    private Integer tokens;

    /** 附件类型: image/audio (NULL=纯文本消息)；聊天 function calling 工具产物挂在 assistant 消息上 */
    private String attachmentType;

    /** 附件文件ID(引用 sys_file.id) */
    private Long attachmentFileId;

    private LocalDateTime createTime;

    /**
     * 附件访问 URL（计算字段，不持久化）。
     * <p>Jackson 序列化时输出 attachmentUrl 字段，前端直接用 src。
     */
    public String getAttachmentUrl() {
        return attachmentFileId != null ? "/file/" + attachmentFileId : null;
    }
}
