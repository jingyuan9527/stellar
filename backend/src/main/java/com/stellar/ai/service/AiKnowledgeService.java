package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.AiKnowledgeBaseDTO;
import com.stellar.ai.entity.AiKnowledgeBase;
import com.stellar.ai.entity.AiKnowledgeChunk;
import com.stellar.ai.mapper.AiKnowledgeBaseMapper;
import com.stellar.ai.mapper.AiKnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * AI 知识库 + RAG：KB CRUD、文档分块(500字+50 overlap)、向量化(调 /v1/embeddings)、语义检索 top-k。
 * <p>向量以 JSON 数组文本存 ai_knowledge_chunk.embedding 列，纯 Java 内存余弦检索——无 pgvector 依赖。
 * 向量化失败不阻断分块入库，仅该批向量留空（重建索引可补）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiKnowledgeService {

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiKnowledgeChunkMapper chunkMapper;
    private final AiEmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;
    private static final int EMBED_BATCH = 32;
    private static final int DEFAULT_TOP_K = 4;
    private static final int VECTOR_CACHE_MAX_KB = 16;
    private final Map<Long, List<CachedChunk>> vectorCache = Collections.synchronizedMap(
            new LinkedHashMap<>(VECTOR_CACHE_MAX_KB, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, List<CachedChunk>> eldest) {
                    return size() > VECTOR_CACHE_MAX_KB;
                }
            });

    // ===== 知识库 CRUD =====

    public List<AiKnowledgeBase> listAll() {
        return kbMapper.selectList(new LambdaQueryWrapper<AiKnowledgeBase>()
                .orderByDesc(AiKnowledgeBase::getUpdateTime));
    }

    public AiKnowledgeBase getKb(Long id) {
        AiKnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        return kb;
    }

    public void createKb(AiKnowledgeBaseDTO dto) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setName(dto.getName().trim());
        kb.setDescription(dto.getDescription());
        kb.setEmbeddingModelId(dto.getEmbeddingModelId());
        kb.setChunkCount(0);
        kb.setCreateTime(LocalDateTime.now());
        kb.setUpdateTime(LocalDateTime.now());
        kbMapper.insert(kb);
        log.info("[知识库] 新增 id={} name={}", kb.getId(), kb.getName());
    }

    public void updateKb(AiKnowledgeBaseDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("知识库 id 不能为空");
        }
        AiKnowledgeBase kb = kbMapper.selectById(dto.getId());
        if (kb == null) {
            throw new BusinessException("知识库不存在");
        }
        kb.setName(dto.getName().trim());
        kb.setDescription(dto.getDescription());
        if (dto.getEmbeddingModelId() != null) {
            kb.setEmbeddingModelId(dto.getEmbeddingModelId());
        }
        kb.setUpdateTime(LocalDateTime.now());
        kbMapper.updateById(kb);
        log.info("[知识库] 更新 id={} name={}", kb.getId(), kb.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteKb(Long id) {
        chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>().eq(AiKnowledgeChunk::getKbId, id));
        kbMapper.deleteById(id);
        invalidateVectorCache(id);
        log.info("[知识库] 删除 id={} (含分块)", id);
    }

    // ===== 分块管理 =====

    public Page<AiKnowledgeChunk> pageChunks(Long kbId, int pageNum, int pageSize) {
        Page<AiKnowledgeChunk> page = new Page<>(pageNum, pageSize);
        return chunkMapper.selectPage(page, new LambdaQueryWrapper<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getKbId, kbId)
                .orderByAsc(AiKnowledgeChunk::getChunkIndex));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteChunk(Long id) {
        AiKnowledgeChunk chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            return;
        }
        chunkMapper.deleteById(id);
        refreshChunkCount(chunk.getKbId());
        invalidateVectorCache(chunk.getKbId());
    }

    /**
     * 添加文档：分块 + 入库 + 向量化。
     */
    public int addDocument(Long kbId, String text, String sourceName) {
        AiKnowledgeBase kb = getKb(kbId);
        List<String> pieces = chunk(text, CHUNK_SIZE, CHUNK_OVERLAP);
        if (pieces.isEmpty()) {
            throw new BusinessException("文档分块为空");
        }
        // 分块入库放在独立事务，提交后再做外部向量化——避免长网络调用占用 DB 连接、拖住事务
        // 用 TransactionTemplate 编程式事务而非 @Transactional：insertChunks 是 private 自调用，
        // Spring AOP 代理无法拦截，注解不生效
        List<AiKnowledgeChunk> inserted = new TransactionTemplate(transactionManager)
                .execute(status -> insertChunks(kbId, pieces, sourceName));
        // 批量向量化回填 embedding 列（失败不阻断分块入库，仅日志告警）
        vectorizeChunks(kb, inserted);
        refreshChunkCount(kbId);
        invalidateVectorCache(kbId);
        log.info("[知识库] 添加文档 kbId={} source={} chunks={}", kbId, sourceName, inserted.size());
        return inserted.size();
    }

    /** 分块持久化（由调用方通过 TransactionTemplate 包裹，保证全成功或全回滚）。 */
    private List<AiKnowledgeChunk> insertChunks(Long kbId, List<String> pieces, String sourceName) {
        List<AiKnowledgeChunk> inserted = new ArrayList<>();
        int idx = 0;
        for (String piece : pieces) {
            AiKnowledgeChunk c = new AiKnowledgeChunk();
            c.setKbId(kbId);
            c.setChunkText(piece);
            c.setChunkIndex(idx++);
            c.setTokenCount(piece.length());
            c.setSourceName(sourceName);
            c.setCreateTime(LocalDateTime.now());
            chunkMapper.insert(c);
            inserted.add(c);
        }
        return inserted;
    }

    /**
     * 重建索引：对知识库所有分块重新向量化（模型变更或补未向量化的分块时用）。
     * <p>无原子性需求（向量化失败已 best-effort 捕获），不包裹事务，避免长网络调用占用 DB 连接。
     */
    public void rebuild(Long kbId) {
        AiKnowledgeBase kb = getKb(kbId);
        List<AiKnowledgeChunk> all = chunkMapper.selectList(
                new LambdaQueryWrapper<AiKnowledgeChunk>().eq(AiKnowledgeChunk::getKbId, kbId));
        if (all.isEmpty()) {
            throw new BusinessException("知识库无分块");
        }
        vectorizeChunks(kb, all);
        invalidateVectorCache(kbId);
        log.info("[知识库] 重建索引完成 kbId={} chunks={}", kbId, all.size());
    }

    /**
     * 语义检索：查询文本向量化后，加载该知识库全量向量在内存做余弦相似度 top-k。
     * <p>返回分块详情（含 chunkId/sourceName/score），供 RAG 管线（RRF 融合/重排/引用溯源）使用。
     * <p>无向量数据或查询向量化失败时返回空列表（RAG 不注入）。
     */
    public List<ScoredChunk> searchDetailed(Long kbId, String query, int topK) {
        AiKnowledgeBase kb = getKb(kbId);
        float[] qvec;
        try {
            qvec = embeddingService.embed(query, kb.getEmbeddingModelId());
        } catch (Exception e) {
            log.warn("[知识库] 查询向量化失败，RAG 跳过: {}", e.getMessage());
            return List.of();
        }
        // 加载该 KB 全量带向量分块（embedding 不在实体默认 select，用手写 SQL 查）
        List<CachedChunk> chunks = getCachedChunks(kbId);
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>();
        for (CachedChunk chunk : chunks) {
            scored.add(new Scored(chunk.id(), chunk.text(), chunk.sourceName(), VectorOps.cosine(qvec, chunk.vector())));
        }
        scored.sort((a, b) -> Double.compare(b.sim, a.sim));
        int k = topK > 0 ? topK : DEFAULT_TOP_K;
        List<ScoredChunk> result = new ArrayList<>(k);
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            Scored s = scored.get(i);
            result.add(new ScoredChunk(s.chunkId, s.chunkText, s.sourceName, s.sim));
        }
        return result;
    }

    /**
     * 语义检索（仅返回文本），兼容旧调用与测试。委托 {@link #searchDetailed}。
     */
    public List<String> search(Long kbId, String query, int topK) {
        return searchDetailed(kbId, query, topK).stream().map(ScoredChunk::text).toList();
    }

    // ===== 内部 =====

    /**
     * 批量向量化并回填 embedding 列。按 EMBEDB_BATCH 分批调用，避免单次输入过长。
     */
    private void vectorizeChunks(AiKnowledgeBase kb, List<AiKnowledgeChunk> chunks) {
        Long modelId = kb.getEmbeddingModelId();
        for (int i = 0; i < chunks.size(); i += EMBED_BATCH) {
            int end = Math.min(i + EMBED_BATCH, chunks.size());
            List<AiKnowledgeChunk> sub = chunks.subList(i, end);
            List<String> texts = sub.stream().map(AiKnowledgeChunk::getChunkText).toList();
            List<float[]> vectors;
            try {
                vectors = embeddingService.embedBatch(texts, modelId);
            } catch (Exception e) {
                log.error("[知识库] 批量向量化失败 batch={}..{}: {}", i, end, e.getMessage(), e);
                continue;
            }
            for (int j = 0; j < sub.size() && j < vectors.size(); j++) {
                String literal = embeddingService.toVectorLiteral(vectors.get(j));
                jdbcTemplate.update(
                        "UPDATE ai_knowledge_chunk SET embedding = ? WHERE id = ?",
                        literal, sub.get(j).getId());
            }
        }
    }

    private void refreshChunkCount(Long kbId) {
        Long count = chunkMapper.selectCount(
                new LambdaQueryWrapper<AiKnowledgeChunk>().eq(AiKnowledgeChunk::getKbId, kbId));
        AiKnowledgeBase upd = new AiKnowledgeBase();
        upd.setId(kbId);
        upd.setChunkCount(count == null ? 0 : count.intValue());
        upd.setUpdateTime(LocalDateTime.now());
        kbMapper.updateById(upd);
    }

    /**
     * 按字符数分块（含 overlap）。中文按字符切即可。
     */
    private List<String> chunk(String text, int size, int overlap) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return result;
        }
        String normalized = text.replaceAll("\r\n", "\n");
        int len = normalized.length();
        int step = Math.max(1, size - overlap);
        for (int i = 0; i < len; i += step) {
            int end = Math.min(i + size, len);
            String piece = normalized.substring(i, end).strip();
            if (!piece.isEmpty()) {
                result.add(piece);
            }
            if (end == len) {
                break;
            }
        }
        return result;
    }

    private List<CachedChunk> getCachedChunks(Long kbId) {
        synchronized (vectorCache) {
            List<CachedChunk> cached = vectorCache.get(kbId);
            if (cached != null) {
                return cached;
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, chunk_text, source_name, embedding FROM ai_knowledge_chunk "
                            + "WHERE kb_id=? AND embedding IS NOT NULL", kbId);
            List<CachedChunk> loaded = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                float[] vector = VectorOps.parseVector((String) row.get("embedding"));
                if (vector != null && vector.length > 0) {
                    Number id = (Number) row.get("id");
                    loaded.add(new CachedChunk(id.longValue(), (String) row.get("chunk_text"),
                            (String) row.get("source_name"), vector));
                }
            }
            List<CachedChunk> immutable = List.copyOf(loaded);
            vectorCache.put(kbId, immutable);
            return immutable;
        }
    }

    private void invalidateVectorCache(Long kbId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    vectorCache.remove(kbId);
                }
            });
            return;
        }
        vectorCache.remove(kbId);
    }

    /** 检索结果详情（含 chunkId/sourceName/score），供 RAG 管线使用。 */
    public record ScoredChunk(Long chunkId, String text, String sourceName, double score) {}

    private record Scored(Long chunkId, String chunkText, String sourceName, double sim) {}
    private record CachedChunk(long id, String text, String sourceName, float[] vector) {}
}
