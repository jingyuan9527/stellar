package com.stellar.ai.service.rag;

import com.stellar.memos.service.MemosRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 备忘笔记检索器：语义检索备份笔记（仅登录用户启用），复用 {@link MemosRagService}。
 * <p>向量化用 EMBEDDING 默认模型；qvec 由 {@link RagSearchService} 统一计算（与 KB 默认模型共享，避免重复向量化）；
 * 召回失败返回空列表（不打断管线）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemosRetriever {

    private final MemosRagService memosRagService;

    /** 稠密向量通道：qvec 必须用默认 EMBEDDING 模型计算（null 则不检索）。 */
    public List<RagHit> retrieve(float[] qvec, int topK) {
        if (qvec == null) {
            return List.of();
        }
        try {
            return memosRagService.searchWithVector(qvec, topK).stream()
                    .map(h -> new RagHit("memos", String.valueOf(h.id()),
                            h.content() == null ? "" : h.content(),
                            h.title(), h.url(), h.score()))
                    .toList();
        } catch (Exception e) {
            log.warn("[RAG管线] 备忘笔记检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 关键词通道（BM25）：精确词/标签/编号召回，与向量通道经 RRF 融合（混合检索）。 */
    public List<RagHit> retrieveKeyword(String query, int topK) {
        try {
            return memosRagService.searchKeyword(query, topK).stream()
                    .map(h -> new RagHit("memos", String.valueOf(h.id()),
                            h.content() == null ? "" : h.content(),
                            h.title(), h.url(), h.score()))
                    .toList();
        } catch (Exception e) {
            log.warn("[RAG管线] 备忘笔记关键词检索失败: {}", e.getMessage());
            return List.of();
        }
    }
}
