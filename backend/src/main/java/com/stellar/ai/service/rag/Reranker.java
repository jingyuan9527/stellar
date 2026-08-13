package com.stellar.ai.service.rag;

import java.util.List;

/**
 * 重排组件（RAG 管线倒数第二阶段）：对融合/过滤后的候选按与问题相关性精排 top-k。
 * <p>企业级用 cross-encoder 精排（本地需部署模型）；本项目用 LLM 精排实现，
 * 二者接口同构，将来可替换为 cross-encoder 实现而不动管线。
 */
public interface Reranker {

    /**
     * 把候选按相关性排序并截断到 topK。失败/异常必须保持原顺序返回（管线降级不中断）。
     *
     * @param query      检索查询（改写后）
     * @param candidates 候选命中（已 RRF 融合 + 阈值过滤）
     * @param topK       保留条数
     * @param modelId    TEXT 模型 id（可空，用 TEXT 默认模型）
     */
    List<RagHit> rerank(String query, List<RagHit> candidates, int topK, Long modelId);
}