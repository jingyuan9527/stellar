package com.stellar.ai.service.rag;

import java.util.List;

/**
 * 外部知识源检索缝：RAG 管线经此接口召回知识库之外的内容（如备忘笔记），
 * 具体实现由各特性模块提供并注册，ai 模块不反向依赖它们。
 */
public interface ExternalRetriever {

    /** 稠密向量通道：qvec 由管线统一用默认 EMBEDDING 模型计算 */
    List<RagHit> retrieve(float[] qvec, int topK);

    /** 关键词通道（BM25）：与向量通道经 RRF 融合（混合检索） */
    List<RagHit> retrieveKeyword(String query, int topK);
}
