package com.stellar.memos.rag;

import com.stellar.ai.service.rag.ExternalRetriever;
import com.stellar.ai.service.rag.RagHit;
import com.stellar.memos.service.MemosRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 备忘笔记检索器：实现 ai RAG 管线的外部知识源缝 {@link ExternalRetriever}（仅登录用户启用），
 * 复用 {@link MemosRagService}。qvec 由管线统一计算（与 KB 默认模型共享，避免重复向量化）；
 * 召回失败返回空列表（不打断管线）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemosRetriever implements ExternalRetriever {

    private final MemosRagService memosRagService;

    @Override
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

    @Override
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
