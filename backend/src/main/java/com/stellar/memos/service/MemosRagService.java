package com.stellar.memos.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.ai.service.AiEmbeddingService;
import com.stellar.ai.service.VectorOps;
import com.stellar.memos.entity.MemosNote;
import com.stellar.memos.mapper.MemosNoteMapper;
import com.stellar.memos.vo.MemosJobResultVO;
import com.stellar.system.service.SysSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

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

    /** LRU 向量缓存：笔记量小（几百条）全量驻留内存毫秒级余弦，超出逐出 */
    private final Map<Long, CachedNote> noteCache = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CachedNote> eldest) {
                    return size() > 128;
                }
            });

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
        List<CachedNote> notes = getCachedNotes();
        if (notes.isEmpty()) {
            return List.of();
        }
        String baseUrl = StringUtils.hasText(sysSettingService.get(KEY_BASE_URL, ""))
                ? sysSettingService.get(KEY_BASE_URL, "") : null;
        List<double[]> scored = new ArrayList<>();
        for (CachedNote n : notes) {
            scored.add(new double[]{n.id(), VectorOps.cosine(qvec, n.vector())});
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

    /** 笔记数据变更（标记删除等不影响内容但需从检索剔除）时由 MemosService 调用清缓存。 */
    public void invalidateCache() {
        noteCache.clear();
    }

    // ===== 内部 =====

    private List<CachedNote> getCachedNotes() {
        synchronized (noteCache) {
            if (!noteCache.isEmpty()) {
                return new ArrayList<>(noteCache.values());
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, uid, content, embedding FROM memos_note "
                            + "WHERE remote_deleted=0 AND embedding IS NOT NULL");
            List<CachedNote> loaded = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                float[] vector = VectorOps.parseVector((String) row.get("embedding"));
                if (vector != null && vector.length > 0) {
                    Number id = (Number) row.get("id");
                    loaded.add(new CachedNote(id.longValue(), (String) row.get("uid"),
                            (String) row.get("content"), vector));
                }
            }
            for (CachedNote n : loaded) {
                noteCache.put(n.id(), n);
            }
            return loaded;
        }
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
