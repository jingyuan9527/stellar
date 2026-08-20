package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.AiKnowledgeBaseDTO;
import com.stellar.ai.entity.AiKnowledgeBase;
import com.stellar.ai.entity.AiKnowledgeChunk;
import com.stellar.ai.mapper.AiKnowledgeBaseMapper;
import com.stellar.ai.mapper.AiKnowledgeChunkMapper;
import com.stellar.ai.service.rag.Bm25Index;
import com.stellar.infra.CacheInvalidationEvent;
import com.stellar.infra.CacheInvalidationMessage;
import com.stellar.infra.CacheInvalidationPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * 向量检索缓存（Caffeine 带驱逐，防 OOM）。{@code get(k, loader)} 同 kb 原子单飞加载
     * （并发只查一次库、不持全局锁），不同 kb 完全并发；加载期间不占任何锁。
     */
    private final Cache<Long, List<CachedChunk>> vectorCache = Caffeine.newBuilder()
            .maximumSize(VECTOR_CACHE_MAX_KB)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    /** BM25 倒排索引缓存（与 vectorCache 同 key 同生命周期，随数据变更一起失效）。 */
    private final Cache<Long, Bm25Index> bm25Cache = Caffeine.newBuilder()
            .maximumSize(VECTOR_CACHE_MAX_KB)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    /**
     * 多实例缓存失效广播（可选注入：单测直接 new 时为 null，跳过广播）。
     */
    @Autowired(required = false)
    private CacheInvalidationPublisher cacheInvalidationPublisher;

    /** 重建索引并发锁：连点/并发触发时拒绝后到者，避免重复向量化烧 token。 */
    private final AtomicBoolean rebuildLock = new AtomicBoolean(false);

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
        if (dto.getEmbeddingModelId() != null && !dto.getEmbeddingModelId().equals(kb.getEmbeddingModelId())) {
            // 换向量化模型：旧维度向量与新模型不兼容（cosine 维度不一致恒为 0，检索会静默返回垃圾），
            // 直接清空该 KB 全部分块向量，检索返回空（明确语义），重建索引后恢复。
            jdbcTemplate.update("UPDATE ai_knowledge_chunk SET embedding = NULL WHERE kb_id = ?", dto.getId());
            log.warn("[知识库] 切换 embedding 模型 {} -> {}，已清空全部分块向量，请重建索引",
                    kb.getEmbeddingModelId(), dto.getEmbeddingModelId());
            kb.setEmbeddingModelId(dto.getEmbeddingModelId());
        }
        kb.setUpdateTime(LocalDateTime.now());
        kbMapper.updateById(kb);
        invalidateIndexCache(dto.getId());
        log.info("[知识库] 更新 id={} name={}", kb.getId(), kb.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteKb(Long id) {
        chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>().eq(AiKnowledgeChunk::getKbId, id));
        kbMapper.deleteById(id);
        invalidateIndexCache(id);
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
        invalidateIndexCache(chunk.getKbId());
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
        invalidateIndexCache(kbId);
        log.info("[知识库] 添加文档 kbId={} source={} chunks={}", kbId, sourceName, inserted.size());
        return inserted.size();
    }

    /**
     * 更新文档：按来源名替换全部旧分块（删旧 + 重新分块入库，同一事务），再向量化新分块。
     * 解决"改文档只能删了重加"的问题。
     */
    public int updateDocument(Long kbId, String sourceName, String text) {
        if (!StringUtils.hasText(sourceName)) {
            throw new BusinessException("来源名称不能为空");
        }
        AiKnowledgeBase kb = getKb(kbId);
        List<AiKnowledgeChunk> inserted = new TransactionTemplate(transactionManager)
                .execute(status -> {
                    chunkMapper.delete(new LambdaQueryWrapper<AiKnowledgeChunk>()
                            .eq(AiKnowledgeChunk::getKbId, kbId)
                            .eq(AiKnowledgeChunk::getSourceName, sourceName));
                    List<String> pieces = chunk(text, CHUNK_SIZE, CHUNK_OVERLAP);
                    if (pieces.isEmpty()) {
                        throw new BusinessException("文档分块为空");
                    }
                    return insertChunks(kbId, pieces, sourceName);
                });
        vectorizeChunks(kb, inserted);
        refreshChunkCount(kbId);
        invalidateIndexCache(kbId);
        log.info("[知识库] 更新文档 kbId={} source={} chunks={}", kbId, sourceName, inserted.size());
        return inserted.size();
    }

    /** 该知识库全部文档来源名（分块管理页"更新文档"下拉用，DISTINCT 去重）。 */
    public List<String> listSources(Long kbId) {
        getKb(kbId);
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT source_name FROM ai_knowledge_chunk WHERE kb_id=? "
                        + "AND source_name IS NOT NULL ORDER BY source_name", String.class, kbId);
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
        if (!rebuildLock.compareAndSet(false, true)) {
            throw new BusinessException("重建索引进行中，请稍后再试");
        }
        try {
            AiKnowledgeBase kb = getKb(kbId);
            List<AiKnowledgeChunk> all = chunkMapper.selectList(
                    new LambdaQueryWrapper<AiKnowledgeChunk>().eq(AiKnowledgeChunk::getKbId, kbId));
            if (all.isEmpty()) {
                throw new BusinessException("知识库无分块");
            }
            vectorizeChunks(kb, all);
            invalidateIndexCache(kbId);
            log.info("[知识库] 重建索引完成 kbId={} chunks={}", kbId, all.size());
        } finally {
            rebuildLock.set(false);
        }
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
        return searchDetailedWithVector(kb, qvec, topK);
    }

    /**
     * 检索主体（供 RAG 管线共享 query 向量时调用：调用方保证 qvec 已用本 KB 的 embedding 模型计算，
     * 避免 KB 与备忘双源对同一 query 重复向量化）。kb/qvec 为 null 返回空（调用方已降级）。
     */
    public List<ScoredChunk> searchDetailedWithVector(AiKnowledgeBase kb, float[] qvec, int topK) {
        if (kb == null || qvec == null) {
            return List.of();
        }
        // 加载该 KB 全量分块（embedding 不在实体默认 select，用手写 SQL 查；未向量化分块 vector=null）
        List<CachedChunk> chunks = getCachedChunks(kb.getId());
        if (chunks.isEmpty()) {
            return List.of();
        }
        // 维度防御：切换 embedding 模型后旧分块向量维度不匹配，cosine 恒为 0 会静默返回垃圾 top-k。
        // 策略：跳过维度不匹配的向量（记录告警），用剩余可用向量检索；全部不匹配才返回空（提示重建索引）。
        // 不采用"任一不匹配即全空"：混合维度（部分旧模型）时不应把可用向量一起丢掉。
        List<Scored> scored = new ArrayList<>();
        int dimSkipped = 0;
        for (CachedChunk chunk : chunks) {
            float[] v = chunk.vector();
            if (v == null) {
                continue;
            }
            if (v.length != qvec.length) {
                dimSkipped++;
                continue;
            }
            scored.add(new Scored(chunk.id(), chunk.text(), chunk.sourceName(), VectorOps.cosine(qvec, v)));
        }
        if (scored.isEmpty()) {
            if (dimSkipped > 0) {
                log.warn("[知识库] 全部分块向量维度不一致 kbId={} 查询维度={}，请重建索引（切模型后旧向量已失效）",
                        kb.getId(), qvec.length);
            }
            return List.of();
        }
        if (dimSkipped > 0) {
            log.warn("[知识库] 跳过 {} 条维度不匹配分块 kbId={} 查询维度={}，建议重建索引", dimSkipped, kb.getId(), qvec.length);
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
     * 关键词检索（BM25 倒排，零依赖）：专有名词/编号/标签等精确词召回，
     * 与 {@link #searchDetailed} 的稠密向量检索经 RRF 融合组成混合检索（见 RagSearchService）。
     */
    public List<ScoredChunk> searchDetailedKeyword(Long kbId, String query, int topK) {
        return searchDetailedKeywordWith(getKb(kbId), query, topK);
    }

    /**
     * BM25 主体（供 RAG 管线传入已解析的 KB，避免每轮重复存在性查询）。kb 为 null 返回空。
     */
    public List<ScoredChunk> searchDetailedKeywordWith(AiKnowledgeBase kb, String query, int topK) {
        if (kb == null) {
            return List.of();
        }
        List<CachedChunk> chunks = getCachedChunks(kb.getId());
        if (chunks.isEmpty()) {
            return List.of();
        }
        Bm25Index index = getBm25Index(kb.getId(), chunks);
        List<Bm25Index.Score> scores = index.search(query, topK > 0 ? topK : DEFAULT_TOP_K);
        List<ScoredChunk> result = new ArrayList<>(scores.size());
        for (Bm25Index.Score s : scores) {
            CachedChunk c = chunks.get(s.docIndex());
            result.add(new ScoredChunk(c.id(), c.text(), c.sourceName(), s.score()));
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
        // Caffeine get(k, loader)：同 kb 原子单飞（并发仅一次查库），不同 kb 并发，加载不持锁
        return vectorCache.get(kbId, this::loadChunksFromDb);
    }

    /** 全量加载分块（含未向量化分块 vector=null）：BM25 关键词通道不依赖向量，与稠密通道互补。 */
    private List<CachedChunk> loadChunksFromDb(Long kbId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, chunk_text, source_name, embedding FROM ai_knowledge_chunk WHERE kb_id=?", kbId);
        List<CachedChunk> loaded = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Number id = (Number) row.get("id");
            if (id == null) {
                continue;
            }
            float[] vector = VectorOps.parseVector((String) row.get("embedding"));
            loaded.add(new CachedChunk(id.longValue(), (String) row.get("chunk_text"),
                    (String) row.get("source_name"), vector == null || vector.length == 0 ? null : vector));
        }
        return List.copyOf(loaded);
    }

    /** 数据变更（增删改文档/重建/切模型）后失效向量与 BM25 两套检索缓存，并广播各实例。 */
    private void invalidateIndexCache(Long kbId) {
        Runnable invalidate = () -> {
            vectorCache.invalidate(kbId);
            bm25Cache.invalidate(kbId);
            publishInvalidation(CacheInvalidationMessage.SCOPE_KB, String.valueOf(kbId));
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate.run();
                }
            });
            return;
        }
        invalidate.run();
    }

    private void publishInvalidation(String scope, String key) {
        if (cacheInvalidationPublisher != null) {
            cacheInvalidationPublisher.publish(scope, key);
        }
    }

    /** 其他实例数据变更广播 → 本实例失效对应 KB 缓存（多实例一致性）。 */
    @EventListener
    public void onCacheInvalidation(CacheInvalidationEvent event) {
        if (!CacheInvalidationMessage.SCOPE_KB.equals(event.scope())) {
            return;
        }
        try {
            Long kbId = Long.valueOf(event.key());
            vectorCache.invalidate(kbId);
            bm25Cache.invalidate(kbId);
            log.debug("[知识库] 收到远端缓存失效 kbId={}", event.key());
        } catch (Exception e) {
            log.warn("[知识库] 远端缓存失效处理失败 key={}: {}", event.key(), e.getMessage());
        }
    }

    /** BM25 索引按 kbId 缓存（与 vectorCache 同源同序：docIndex 对齐 CachedChunk 列表下标）。 */
    private Bm25Index getBm25Index(Long kbId, List<CachedChunk> chunks) {
        return bm25Cache.get(kbId, k -> Bm25Index.build(chunks.stream().map(CachedChunk::text).toList()));
    }

    /** 检索结果详情（含 chunkId/sourceName/score），供 RAG 管线使用。 */
    public record ScoredChunk(Long chunkId, String text, String sourceName, double score) {}

    private record Scored(Long chunkId, String chunkText, String sourceName, double sim) {}
    private record CachedChunk(long id, String text, String sourceName, float[] vector) {}
}
