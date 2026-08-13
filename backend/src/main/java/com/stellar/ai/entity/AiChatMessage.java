package com.stellar.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stellar.ai.vo.RagSource;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    /** RAG 引用来源（JSON 数组文本 [{source,key,title,url,score}]），assistant 消息落库，溯源用 */
    private String ragRefs;

    /**
     * 当前主体对该消息的评价（1 有用 / -1 没用 / null 未评价）。
     * <p>计算字段不落库：getMessages 按主体查 rag_feedback 回填，前端回显"有用/没用"选中态。
     */
    @TableField(exist = false)
    private Integer feedbackValue;

    private LocalDateTime createTime;

    /**
     * RAG 引用列表（计算字段，不持久化）。
     * <p>解析 {@code ragRefs} JSON，Jackson 序列化时输出 refs 数组，前端气泡渲染参考链接。
     */
    public List<RagSource> getRefs() {
        return RagSource.parse(ragRefs);
    }

    /**
     * 附件访问 URL（计算字段，不持久化）。
     * <p>Jackson 序列化时输出 attachmentUrl 字段，前端直接用 src。
     */
    public String getAttachmentUrl() {
        return attachmentFileId != null ? "/file/" + attachmentFileId : null;
    }
}
