package com.stellar.ai.service.rag;

/**
 * 查询改写组件（RAG 管线第一阶段）。
 * <p>把用户的原始提问改写为更适合语义检索的查询（去口语冗余/补全指代/明确主题），
 * 比直接拿原文向量化召回更准。企业级可替换为 multi-query 扩写 + 多路改写。
 * loop 模式（期3）下带 gap 参数：把上一轮 Judger 的缺口反馈进改写方向补查。
 */
public interface QueryRewriter {

    /**
     * 改写查询（单轮模式）。失败/异常必须返回原查询（管线降级不中断）。
     */
    default String rewrite(String query, Long modelId) {
        return rewrite(query, null, modelId);
    }

    /**
     * 改写查询（含 loop 补查缺口）。失败/异常必须返回原查询（管线降级不中断）。
     *
     * @param query   原始用户问题
     * @param gap     上一轮判定缺的资料（可空；非空时改写向"补足缺口"方向扩展）
     * @param modelId TEXT 模型 id（可空，用 TEXT 默认模型）
     * @return 改写后的查询；失败返回原 query
     */
    String rewrite(String query, String gap, Long modelId);
}