package com.stellar.ai.service.rag;

import com.stellar.ai.service.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库检索器：按会话关联的知识库 id 召回分块，复用 {@link AiKnowledgeService#searchDetailed}。
 * <p>向量化用该 KB 绑定的 EMBEDDING 模型；kbId 为空返回空列表（不打断管线）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbRetriever {

    private final AiKnowledgeService knowledgeService;

    public List<RagHit> retrieve(Long kbId, String query, int topK) {
        if (kbId == null) {
            return List.of();
        }
        try {
            return knowledgeService.searchDetailed(kbId, query, topK).stream()
                    .map(c -> new RagHit("kb", String.valueOf(c.chunkId()),
                            c.text() == null ? "" : c.text(),
                            c.sourceName(), null, c.score()))
                    .toList();
        } catch (Exception e) {
            log.warn("[RAG管线] 知识库检索失败 kbId={}: {}", kbId, e.getMessage());
            return List.of();
        }
    }
}