package com.stellar.ai.service.rag;

import com.stellar.memos.service.MemosRagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 备忘笔记检索器：语义检索备份笔记（仅登录用户启用），复用 {@link MemosRagService#search}。
 * <p>向量化用 EMBEDDING 默认模型；召回失败返回空列表（不打断管线）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemosRetriever {

    private final MemosRagService memosRagService;

    public List<RagHit> retrieve(String query, int topK) {
        try {
            return memosRagService.search(query, topK).stream()
                    .map(h -> new RagHit("memos", String.valueOf(h.id()),
                            h.content() == null ? "" : h.content(),
                            h.title(), h.url(), h.score()))
                    .toList();
        } catch (Exception e) {
            log.warn("[RAG管线] 备忘笔记检索失败: {}", e.getMessage());
            return List.of();
        }
    }
}