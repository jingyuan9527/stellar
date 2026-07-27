package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 知识库分块：文档分块文本。embedding 存 JSON 数组文本，纯 Java 内存余弦检索，默认查询不加载。
 */
@Data
@TableName("ai_knowledge_chunk")
public class AiKnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String chunkText;

    private Integer chunkIndex;

    private Integer tokenCount;

    private String sourceName;

    /** 向量(JSON数组文本 [v1,v2,...])，默认查询不加载，检索时单独查 */
    @TableField(select = false)
    private String embedding;

    private LocalDateTime createTime;
}
