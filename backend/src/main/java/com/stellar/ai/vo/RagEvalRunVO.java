package com.stellar.ai.vo;

import java.util.List;

/**
 * RAG 跑分结果（一次 run 的汇总 + 明细）。detail/hit 字段仅供前端展示，不落库（滚存由 rag_eval_result 承载）。
 */
public record RagEvalRunVO(
        String runId,
        String mode,
        int total,
        int passCount,
        int failCount,
        double recallAvg,
        List<RagEvalDetailVO> details
) {
    public record RagEvalDetailVO(
            Long caseId,
            String query,
            boolean pass,
            double recall,
            List<RagEvalHitVO> topHits
    ) {
    }

    /** 检索 top-k 命中（来源 + 标题 + 分数，供复盘"召回了什么、没召回到什么"） */
    public record RagEvalHitVO(
            String source,
            String title,
            String url,
            double score
    ) {
    }
}