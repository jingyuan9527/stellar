package com.stellar.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI RAG 跑分结果（按 run_id 批次）：每次 runEvaluation 对每个评估用例记一条。
 * 保留历史批次的 pass/recall 供回归对比（改检索/分块/阈值后跑分防改坏）。
 */
@Data
@TableName("rag_eval_result")
public class RagEvalResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 跑分批次ID */
    private String runId;

    /** 评估用例ID */
    private Long caseId;

    /** 该用例问题快照（跑分日志可读性） */
    private String query;

    /** 检索 top-k 命中(JSON数组文本 [{source,title,url,score}]) */
    private String topHits;

    /** 是否命中期望来源: 1是 0否 */
    private Integer pass;

    /** 期望来源召回率(命中数/期望数) */
    private Double recall;

    /** 跑分模式: retrieval=纯检索路径 / full=完整管线(含改写/重排/loop) */
    private String mode;

    private LocalDateTime createTime;
}