package com.stellar.ai.service.rag;

/**
 * RAG 检索命中条目（多源统一模型）。
 * <p>知识库分块与备忘笔记经各自 Retriever 召回后统一为 {@code RagHit}，
 * 供 RRF 融合、阈值过滤、LLM 重排、system 注入与引用溯源使用。
 *
 * @param source    来源类型: kb=知识库 chunk / memos=备忘笔记
 * @param sourceKey 来源标识: chunkId 或 noteId（字符串）
 * @param text      命中文本（注入 system 的片段）
 * @param title     来源标题: KB sourceName / memos 首行，可空
 * @param url       原始链接: memos 为 {base}/m/{uid}，KB 为 null
 * @param score     原始检索分（各源余弦分；RRF 融合后为融合分）
 */
public record RagHit(String source, String sourceKey, String text, String title, String url, double score) {
}