package com.stellar.ai.vo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;

/**
 * RAG 引用来源（回答实际召回并注入的资料条目）。
 * <p>注入层：聊天 system 里用 {@code [来源N]} 编号让 LLM 引用；
 * 追溯层：assistant 消息 {@code rag_refs} 列存本 JSON 数组，前端气泡下方渲染"参考"链接列，
 * 点击跳原始资料（memos 笔记 {@code /m/{uid}}，知识库分块无 URL 仅展示来源名）。
 *
 * @param source    来源类型: kb=知识库 chunk / memos=备忘笔记
 * @param sourceKey 来源标识: chunkId 或 noteId（字符串）
 * @param title     来源标题: KB sourceName / memos 首行，可空
 * @param url       原始链接: memos 为 {base}/m/{uid}，KB 为 null
 * @param score     检索相似度分（RRF 融合后）
 */
public record RagSource(String source, String sourceKey, String title, String url, double score) {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    /** 序列化引用列表为 JSON 数组文本（落 rag_refs 列）。空列表返回 null。 */
    public static String toJson(List<RagSource> refs) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(refs);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 rag_refs 列 JSON 为引用列表；空/非法返回空列表。 */
    public static List<RagSource> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<RagSource>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
