package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.dto.AiKnowledgeBaseDTO;
import com.stellar.entity.AiKnowledgeBase;
import com.stellar.entity.AiKnowledgeChunk;
import com.stellar.mapper.AiKnowledgeBaseMapper;
import com.stellar.mapper.AiKnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;
    private static final int EMBED_BATCH = 32;
    private static final int DEFAULT_TOP_K = 4;

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
    }

    /**
     * 添加文档：分块 + 入库 + 向量化。
     */
    @Transactional(rollbackFor = Exception.class)
    public int addDocument(Long kbId, String text, String sourceName) {
        AiKnowledgeBase kb = getKb(kbId);
        List<String> pieces = chunk(text, CHUNK_SIZE, CHUNK_OVERLAP);
        if (pieces.isEmpty()) {
            throw new BusinessException("文档分块为空");
        }
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
        // 批量向量化回填 embedding 列（失败不阻断分块入库，仅日志告警）
        vectorizeChunks(kb, inserted);
        refreshChunkCount(kbId);
        log.info("[知识库] 添加文档 kbId={} source={} chunks={}", kbId, sourceName, inserted.size());
        return inserted.size();
    }

    /**
     * 重建索引：对知识库所有分块重新向量化（模型变更或补未向量化的分块时用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rebuild(Long kbId) {
        AiKnowledgeBase kb = getKb(kbId);
        List<AiKnowledgeChunk> all = chunkMapper.selectList(
                new LambdaQueryWrapper<AiKnowledgeChunk>().eq(AiKnowledgeChunk::getKbId, kbId));
        if (all.isEmpty()) {
            throw new BusinessException("知识库无分块");
        }
        vectorizeChunks(kb, all);
        log.info("[知识库] 重建索引完成 kbId={} chunks={}", kbId, all.size());
    }

    /**
     * 语义检索：查询文本向量化后，加载该知识库全量向量在内存做余弦相似度 top-k。
     * <p>无向量数据或查询向量化失败时返回空列表（RAG 不注入）。
     */
    public List<String> search(Long kbId, String query, int topK) {
        AiKnowledgeBase kb = getKb(kbId);
        float[] qvec;
        try {
            qvec = embeddingService.embed(query, kb.getEmbeddingModelId());
        } catch (Exception e) {
            log.warn("[知识库] 查询向量化失败，RAG 跳过: {}", e.getMessage());
            return List.of();
        }
        // 加载该 KB 全量带向量分块（embedding 不在实体默认 select，用手写 SQL 查）
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT chunk_text, embedding FROM ai_knowledge_chunk " +
                        "WHERE kb_id=? AND embedding IS NOT NULL", kbId);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Scored> scored = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            float[] vec = parseVector((String) row.get("embedding"));
            if (vec == null || vec.length == 0) continue;
            scored.add(new Scored((String) row.get("chunk_text"), cosine(qvec, vec)));
        }
        scored.sort((a, b) -> Double.compare(b.sim, a.sim));
        int k = topK > 0 ? topK : DEFAULT_TOP_K;
        List<String> result = new ArrayList<>(k);
        for (int i = 0; i < Math.min(k, scored.size()); i++) {
            result.add(scored.get(i).chunkText);
        }
        return result;
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

    /**
     * 解析 embedding 文本 '[1.0,2.0,...]' 为 float[]。非法返回 null。
     */
    private float[] parseVector(String text) {
        if (text == null || text.isBlank()) return null;
        String s = text.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        if (s.isBlank()) return null;
        String[] parts = s.split(",");
        float[] vec = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Float.parseFloat(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return vec;
    }

    /**
     * 余弦相似度。任一为零向量返回 0。
     */
    private double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private record Scored(String chunkText, double sim) {}
}
