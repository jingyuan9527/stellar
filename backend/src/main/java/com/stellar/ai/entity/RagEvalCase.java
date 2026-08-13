package com.stellar.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI RAG 评估用例（golden set）：一条"问题 + 期望命中来源"，
 * 跑分时对该问题跑纯检索路径算 recall@k，用于回归防改坏 + bad case 积累。
 */
@Data
@TableName("rag_eval_case")
public class RagEvalCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户问题（golden，与线上同样的提问方式） */
    private String query;

    /** 关联知识库ID(可空，空=仅备忘笔记源) */
    private Long kbId;

    /** 期望命中的来源key(JSON数组文本, 如 ["memos:12","kb:3"]) */
    private String expectedSources;

    /** 备注(为什么期望这些来源/问题背景) */
    private String note;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}