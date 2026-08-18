package com.stellar.ai.service.rag;

import com.stellar.ai.entity.AiKnowledgeBase;
import com.stellar.ai.service.AiKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库检索器：按会话关联的知识库召回分块，复用 {@link AiKnowledgeService}。
 * <p>向量化用该 KB 绑定的 EMBEDDING 模型；kb 为 null 返回空列表（不打断管线）。
 * <p>由 {@link RagSearchService} 每轮先 {@link #getKbContext} 解析一次 KB（存在性 + 绑定模型），
 * 并把共享 query 向量传入 {@link #retrieve}（KB 用默认模型时与备忘源共用同一向量，避免重复向量化）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbRetriever {

    private final AiKnowledgeService knowledgeService;

    /** 管线级解析一次 KB：返回 null 表示未关联或不存在（调用方降级该源）。 */
    public AiKnowledgeBase getKbContext(Long kbId) {
        if (kbId == null) {
            return null;
        }
        try {
            return knowledgeService.getKb(kbId);
        } catch (Exception e) {
            log.warn("[RAG管线] 知识库不存在或读取失败 kbId={}: {}", kbId, e.getMessage());
            return null;
        }
    }

    /** 稠密向量通道：qvec 必须用本 KB 的 embedding 模型计算（null 则不检索）。 */
    public List<RagHit> retrieve(AiKnowledgeBase kb, float[] qvec, int topK) {
        if (kb == null || qvec == null) {
            return List.of();
        }
        try {
            return knowledgeService.searchDetailedWithVector(kb, qvec, topK).stream()
                    .map(c -> new RagHit("kb", String.valueOf(c.chunkId()),
                            c.text() == null ? "" : c.text(),
                            c.sourceName(), null, c.score()))
                    .toList();
        } catch (Exception e) {
            log.warn("[RAG管线] 知识库检索失败 kbId={}: {}", kb.getId(), e.getMessage());
            return List.of();
        }
    }

    /** 关键词通道（BM25）：精确词/专有名词/编号召回，与向量通道经 RRF 融合（混合检索）。 */
    public List<RagHit> retrieveKeyword(AiKnowledgeBase kb, String query, int topK) {
        if (kb == null) {
            return List.of();
        }
        try {
            return knowledgeService.searchDetailedKeywordWith(kb, query, topK).stream()
                    .map(c -> new RagHit("kb", String.valueOf(c.chunkId()),
                            c.text() == null ? "" : c.text(),
                            c.sourceName(), null, c.score()))
                    .toList();
        } catch (Exception e) {
            log.warn("[RAG管线] 知识库关键词检索失败 kbId={}: {}", kb.getId(), e.getMessage());
            return List.of();
        }
    }
}
