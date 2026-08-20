package com.stellar.memos.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stellar.ai.service.AiEmbeddingService;
import com.stellar.ai.service.VectorOps;
import com.stellar.ai.service.rag.Bm25Index;
import com.stellar.common.BusinessException;
import com.stellar.infra.CacheInvalidationEvent;
import com.stellar.infra.CacheInvalidationMessage;
import com.stellar.infra.CacheInvalidationPublisher;
import com.stellar.memos.entity.MemosNote;
import com.stellar.memos.mapper.MemosNoteMapper;
import com.stellar.memos.vo.MemosJobResultVO;
import com.stellar.system.service.SysSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 备忘笔记 RAG：把备份笔记向量化，聊天时按 IP 无关（仅登录）检索注入问答。
 * <p>向量以 JSON 数组文本存 {@code memos_note.embedding} 列，纯 Java 内存余弦检索（无 pgvector 依赖）。
 * 增量：新写/改内容由 {@link MemosService} 挂钩 {@link #embedNoteAsync} 异步向量化，失败不阻断同步主流程；
 * 兜底：{@link #rebuildAll} 全量重建索引（前端备忘同步页或接口触发）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemosRagService {

    private final MemosNoteMapper memosNoteMapper;
    private final AiEmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final SysSettingService sysSettingService;

    private static final int EMBED_BATCH = 32;
    private static final String KEY_BASE_URL = MemosService.KEY_BASE_URL;
    /** 上次全量重建时间（sys_setting），供前端展示 RAG 索引构建状态 */
    private static final String KEY_LAST_REBUILD = "memos_rag_last_rebuild";
    /** 笔记缓存固定单键（全量列表，无分区缓存）。 */
    private static final String CACHE_KEY = "notes";

    /**
     * 笔记向量缓存（Caffeine 单键：get(k, loader) 原子单飞，重建/失效不重复查库，带驱逐防 OOM）。
     * 笔记量小（几百条）全量驻留内存毫秒级余弦。
     */
    private final Cache<String, List<CachedNote>> noteCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    /** BM25 倒排索引缓存（与 noteCache 同生命周期：getCachedNotes 重建时一并重建，invalidateCache 时清空）。 */
    private final Cache<String, Bm25Index> noteBm25Cache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    /**
     * 多实例缓存失效广播（可选注入：单测直接 new 时为 null，跳过广播）。
     */
    @Autowired(required = false)
    private CacheInvalidationPublisher cacheInvalidationPublisher;

    /** 全量重建并发锁：连点/并发触发时拒绝后到者，避免重复向量化烧 token。 */
    private final AtomicBoolean rebuildLock = new AtomicBoolean(false);

    /** 检索命中条目（供聊天注入与引用溯源）。 */
    public record MemoHit(long id, String uid, String content, String title, String url, double score) {
    }

    /**
     * 笔记内容变更后异步向量化（由 MemosService 在 insert/merge 后调用，不阻塞同步主流程）。
     * 内部自行吞异常：向量化失败只告警，语义检索暂时查不到该条。
     */
    @Async("aiTaskExecutor")
    public void embedNoteAsync(Long noteId, String content) {
        try {
            if (noteId == null || !StringUtils.hasText(content)) {
                invalidateCache();
                return;
            }
            float[] vector = embeddingService.embed(content, null);
            jdbcTemplate.update("UPDATE memos_note SET embedding = ? WHERE id = ?",
                    VectorOps.toVectorLiteral(vector), noteId);
            invalidateCache();
            log.info("[备忘RAG] 单条向量化完成 noteId={} dim={}", noteId, vector.length);
        } catch (Exception e) {
            log.warn("[备忘RAG] 单条向量化失败（跳过，rebuild 兜底）noteId={}: {}", noteId, e.getMessage());
        }
    }

    /**
     * 全量重建索引：对存活笔记批量向量化回填 embedding 列。
     * 返回处理统计（processed/success/failed），失败仅告警并计数，不阻断整体。
     */
    public MemosJobResultVO rebuildAll() {
        if (!rebuildLock.compareAndSet(false, true)) {
            throw new BusinessException("重建索引进行中，请稍后再试");
        }
        try {
            List<MemosNote> notes = memosNoteMapper.selectList(new LambdaQueryWrapper<MemosNote>()
                    .eq(MemosNote::getRemoteDeleted, 0));
            if (notes.isEmpty()) {
                log.warn("[备忘RAG] 重建索引：无存活笔记");
                return emptyResult();
            }
            long start = System.currentTimeMillis();
            int success = 0, failed = 0;
            for (int i = 0; i < notes.size(); i += EMBED_BATCH) {
                int end = Math.min(i + EMBED_BATCH, notes.size());
                List<MemosNote> sub = notes.subList(i, end);
                List<String> texts = sub.stream().map(n -> n.getContent() == null ? "" : n.getContent()).toList();
                try {
                    List<float[]> vectors = embeddingService.embedBatch(texts, null);
                    for (int j = 0; j < sub.size() && j < vectors.size(); j++) {
                        jdbcTemplate.update("UPDATE memos_note SET embedding = ? WHERE id = ?",
                                VectorOps.toVectorLiteral(vectors.get(j)), sub.get(j).getId());
                    }
                    success += sub.size();
                } catch (Exception e) {
                    failed += sub.size();
                    log.error("[备忘RAG] 批量向量化失败 batch={}..{}: {}", i, end, e.getMessage(), e);
                }
            }
            invalidateCache();
            sysSettingService.set(KEY_LAST_REBUILD, LocalDateTime.now().toString(),
                    "备忘RAG上次全量重建时间");
            log.info("[备忘RAG] 重建索引完成 total={} success={} failed={} 耗时={}ms",
                    notes.size(), success, failed, System.currentTimeMillis() - start);
            MemosJobResultVO vo = new MemosJobResultVO();
            vo.setProcessed(notes.size());
            vo.setSuccess(success);
            vo.setFailed(failed);
            return vo;
        } finally {
            rebuildLock.set(false);
        }
    }

    /**
     * RAG 索引状态（前端展示"是否已构建"）：存活笔记总数/已向量化数/待向量化数/上次全量重建时间。
     * <p>已向量化=remote_deleted=0 且 embedding 非空；待向量化=增量失败或新笔记未赶上，可重建索引补齐。
     */
    public Map<String, Object> status() {
        Map<String, Object> map = new LinkedHashMap<>();
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memos_note WHERE remote_deleted=0", Long.class);
        Long embedded = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memos_note WHERE remote_deleted=0 AND embedding IS NOT NULL", Long.class);
        map.put("total", total == null ? 0L : total);
        map.put("embedded", embedded == null ? 0L : embedded);
        map.put("pending", (total == null ? 0L : total) - (embedded == null ? 0L : embedded));
        map.put("lastRebuildAt", sysSettingService.get(KEY_LAST_REBUILD, ""));
        return map;
    }

    private MemosJobResultVO emptyResult() {
        MemosJobResultVO vo = new MemosJobResultVO();
        vo.setProcessed(0);
        vo.setSuccess(0);
        vo.setFailed(0);
        return vo;
    }

    /**
     * 语义检索生存笔记 top-k（内存余弦）。查询向量化失败或无向量数据返回空列表（RAG 跳过）。
     */
    public List<MemoHit> search(String query, int topK) {
        float[] qvec;
        try {
            qvec = embeddingService.embed(query, null);
        } catch (Exception e) {
            log.warn("[备忘RAG] 查询向量化失败，RAG 跳过: {}", e.getMessage());
            return List.of();
        }
        return searchWithVector(qvec, topK);
    }

    /**
     * 检索主体（供 RAG 管线共享 query 向量时调用：qvec 必须用默认 EMBEDDING 模型计算，
     * 避免 KB 与备忘双源对同一 query 重复向量化）。qvec 为 null 返回空。
     */
    public List<MemoHit> searchWithVector(float[] qvec, int topK) {
        if (qvec == null) {
            return List.of();
        }
        List<CachedNote> notes = getCachedNotes();
        if (notes.isEmpty()) {
            return List.of();
        }
        String baseUrl = StringUtils.hasText(sysSettingService.get(KEY_BASE_URL, ""))
                ? sysSettingService.get(KEY_BASE_URL, "") : null;
        // 维度防御：默认 EMBEDDING 模型切换后旧笔记向量维度不匹配，cosine 恒为 0 会静默返回垃圾 top-k。
        // 策略：跳过维度不匹配的向量（记录告警），用剩余可用向量检索；全部不匹配才返回空（提示重建索引）。
        List<double[]> scored = new ArrayList<>();
        int dimSkipped = 0;
        for (CachedNote n : notes) {
            float[] v = n.vector();
            if (v == null) {
                continue; // 未向量化（增量失败/切模型清空），不参与余弦
            }
            if (v.length != qvec.length) {
                dimSkipped++;
                continue;
            }
            scored.add(new double[]{n.id(), VectorOps.cosine(qvec, v)});
        }
        if (scored.isEmpty()) {
            if (dimSkipped > 0) {
                log.warn("[备忘RAG] 全部笔记向量维度不一致 查询维度={}，请重建索引（换默认 EMBEDDING 模型后旧向量已失效）",
                        qvec.length);
            }
            return List.of();
        }
        if (dimSkipped > 0) {
            log.warn("[备忘RAG] 跳过 {} 条维度不匹配笔记 查询维度={}，建议重建索引", dimSkipped, qvec.length);
        }
        scored.sort((a, b) -> Double.compare(b[1], a[1]));
        int k = topK > 0 ? topK : 4;
        List<MemoHit> result = new ArrayList<>(k);
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            double[] s = scored.get(i);
            long id = (long) s[0];
            CachedNote n = findCached(id, notes);
            if (n == null) {
                continue;
            }
            // Memos web 链接：前端路由为 /memos/{uid}（name 后缀），旧 /m/ 前缀已废弃（SPA 404）
            String url = baseUrl == null ? null : baseUrl + "/memos/" + n.uid();
            result.add(new MemoHit(n.id(), n.uid(), n.content(), firstLine(n.content()), url, s[1]));
        }
        return result;
    }

    /**
     * 关键词检索（BM25 倒排，零依赖）：专有名词/标签/编号精确词召回，
     * 与 {@link #search} 的稠密向量经 RRF 融合组成混合检索（见 RagSearchService）。
     */
    public List<MemoHit> searchKeyword(String query, int topK) {
        List<CachedNote> notes = getCachedNotes();
        if (notes.isEmpty()) {
            return List.of();
        }
        Bm25Index idx = noteBm25Cache.get(CACHE_KEY, k -> Bm25Index.build(notes.stream().map(CachedNote::content).toList()));
        List<Bm25Index.Score> scores = idx.search(query, topK > 0 ? topK : 4);
        if (scores.isEmpty()) {
            return List.of();
        }
        String baseUrl = StringUtils.hasText(sysSettingService.get(KEY_BASE_URL, ""))
                ? sysSettingService.get(KEY_BASE_URL, "") : null;
        List<MemoHit> result = new ArrayList<>(scores.size());
        for (Bm25Index.Score s : scores) {
            CachedNote n = notes.get(s.docIndex());
            String url = baseUrl == null ? null : baseUrl + "/memos/" + n.uid();
            result.add(new MemoHit(n.id(), n.uid(), n.content(), firstLine(n.content()), url, s.score()));
        }
        return result;
    }

    /** 笔记数据变更（标记删除等不影响内容但需从检索剔除）时由 MemosService 调用清缓存，并广播各实例。 */
    public void invalidateCache() {
        noteCache.invalidate(CACHE_KEY);
        noteBm25Cache.invalidate(CACHE_KEY);
        publishInvalidation(CacheInvalidationMessage.SCOPE_MEMOS, null);
    }

    private void publishInvalidation(String scope, String key) {
        if (cacheInvalidationPublisher != null) {
            cacheInvalidationPublisher.publish(scope, key);
        }
    }

    /** 其他实例数据变更广播 → 本实例清空检索缓存（多实例一致性）。 */
    @EventListener
    public void onCacheInvalidation(CacheInvalidationEvent event) {
        if (!CacheInvalidationMessage.SCOPE_MEMOS.equals(event.scope())) {
            return;
        }
        noteCache.invalidate(CACHE_KEY);
        noteBm25Cache.invalidate(CACHE_KEY);
        log.debug("[备忘RAG] 收到远端缓存失效，清空检索缓存");
    }

    // ===== 内部 =====

    private List<CachedNote> getCachedNotes() {
        // Caffeine get(k, loader)：原子单飞加载（并发仅一次查库），不持锁
        return noteCache.get(CACHE_KEY, k -> loadNotes());
    }

    /** 全量加载存活笔记（含未向量化 vector=null）：BM25 关键词通道不依赖向量，与稠密通道互补。 */
    private List<CachedNote> loadNotes() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, uid, content, embedding FROM memos_note WHERE remote_deleted=0");
        List<CachedNote> loaded = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Number id = (Number) row.get("id");
            if (id == null) {
                continue;
            }
            float[] vector = VectorOps.parseVector((String) row.get("embedding"));
            loaded.add(new CachedNote(id.longValue(), (String) row.get("uid"),
                    (String) row.get("content"), vector == null || vector.length == 0 ? null : vector));
        }
        List<CachedNote> immutable = List.copyOf(loaded);
        // BM25 与向量缓存同源同序：docIndex 对齐 immutable 列表下标
        noteBm25Cache.put(CACHE_KEY, Bm25Index.build(immutable.stream().map(CachedNote::content).toList()));
        return immutable;
    }

    private CachedNote findCached(long id, List<CachedNote> notes) {
        for (CachedNote n : notes) {
            if (n.id() == id) {
                return n;
            }
        }
        return null;
    }

    /** 取内容首行（最长 40 字）作笔记标题；空则用 uid 兜底。 */
    private String firstLine(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String line = content.split("\n", 2)[0].trim();
        return line.length() > 40 ? line.substring(0, 40) + "…" : line;
    }

    private record CachedNote(long id, String uid, String content, float[] vector) {
    }
}
