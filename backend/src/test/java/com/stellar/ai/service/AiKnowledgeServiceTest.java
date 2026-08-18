package com.stellar.ai.service;

import com.stellar.ai.dto.AiKnowledgeBaseDTO;
import com.stellar.ai.entity.AiKnowledgeBase;
import com.stellar.ai.entity.AiKnowledgeChunk;
import com.stellar.ai.mapper.AiKnowledgeBaseMapper;
import com.stellar.ai.mapper.AiKnowledgeChunkMapper;
import com.stellar.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.concurrent.atomic.AtomicBoolean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 纯逻辑单元测试：AiKnowledgeService 的检索排序与分块算法。
 * <p>用 Mockito 隔离 5 个协作者（mapper / embeddingService / jdbcTemplate / transactionManager），
 * 不启动 Spring、不连 DB/Redis，直接验证余弦相似度排名与 500 字 + 50 overlap 分块行为。
 */
class AiKnowledgeServiceTest {

    private AiKnowledgeBaseMapper kbMapper;
    private AiKnowledgeChunkMapper chunkMapper;
    private AiEmbeddingService embeddingService;
    private JdbcTemplate jdbcTemplate;
    private PlatformTransactionManager transactionManager;
    private AiKnowledgeService service;

    @BeforeEach
    void setUp() {
        kbMapper = mock(AiKnowledgeBaseMapper.class);
        chunkMapper = mock(AiKnowledgeChunkMapper.class);
        embeddingService = mock(AiEmbeddingService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        transactionManager = mock(PlatformTransactionManager.class);
        service = new AiKnowledgeService(kbMapper, chunkMapper, embeddingService, jdbcTemplate, transactionManager);
    }

    private AiKnowledgeBase kb(Long id, Long modelId) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setId(id);
        kb.setEmbeddingModelId(modelId);
        return kb;
    }

    @Test
    void search_按余弦相似度排序并受topK截断() {
        when(kbMapper.selectById(1L)).thenReturn(kb(1L, 10L));
        when(embeddingService.embed("q", 10L)).thenReturn(new float[]{1f, 0f, 0f});

        Map<String, Object> rowA = new HashMap<>();
        rowA.put("id", 100L);
        rowA.put("chunk_text", "A");
        rowA.put("embedding", "[1.0,0.0,0.0]");
        Map<String, Object> rowB = new HashMap<>();
        rowB.put("id", 101L);
        rowB.put("chunk_text", "B");
        rowB.put("embedding", "[0.0,1.0,0.0]");
        Map<String, Object> rowC = new HashMap<>(); // 无向量，应被过滤
        rowC.put("id", 102L);
        rowC.put("chunk_text", "C");
        rowC.put("embedding", null);
        when(jdbcTemplate.queryForList(anyString(), (Object) any())).thenReturn(List.of(rowA, rowB, rowC));

        List<String> result = service.search(1L, "q", 2);
        assertEquals(List.of("A", "B"), result);
    }

    @Test
    void search_查询向量化失败_返回空() {
        when(kbMapper.selectById(1L)).thenReturn(kb(1L, 10L));
        when(embeddingService.embed(any(), any())).thenThrow(new RuntimeException("boom"));

        assertTrue(service.search(1L, "q", 4).isEmpty());
    }

    @Test
    void search_无分块_返回空() {
        when(kbMapper.selectById(1L)).thenReturn(kb(1L, 10L));
        when(embeddingService.embed(any(), any())).thenReturn(new float[]{1f, 0f, 0f});
        when(jdbcTemplate.queryForList(anyString(), (Object) any())).thenReturn(List.of());

        assertTrue(service.search(1L, "q", 4).isEmpty());
    }

    @Test
    void search_知识库不存在_抛异常() {
        when(kbMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.search(99L, "q", 4));
    }

    @Test
    void updateKb_切换embedding模型_清空分块向量() {
        // 修复点（P1）：换向量化模型必须清空旧维度向量，否则检索静默返回垃圾 top-k
        AiKnowledgeBase kb = kb(5L, 10L);
        kb.setName("旧");
        when(kbMapper.selectById(5L)).thenReturn(kb);

        AiKnowledgeBaseDTO dto = new AiKnowledgeBaseDTO();
        dto.setId(5L);
        dto.setName("新");
        dto.setEmbeddingModelId(20L);
        service.updateKb(dto);

        verify(jdbcTemplate).update(eq("UPDATE ai_knowledge_chunk SET embedding = NULL WHERE kb_id = ?"), eq(5L));
        assertEquals(20L, kb.getEmbeddingModelId());
    }

    @Test
    void updateKb_未切换模型_不清向量() {
        AiKnowledgeBase kb = kb(5L, 10L);
        kb.setName("旧");
        when(kbMapper.selectById(5L)).thenReturn(kb);

        AiKnowledgeBaseDTO dto = new AiKnowledgeBaseDTO();
        dto.setId(5L);
        dto.setName("新");
        dto.setEmbeddingModelId(10L);
        service.updateKb(dto);

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void searchDetailedKeyword_BM25关键词召回() {
        when(kbMapper.selectById(1L)).thenReturn(kb(1L, 10L));
        when(jdbcTemplate.queryForList(anyString(), (Object) any())).thenReturn(List.of(
                row(100L, "部署图床方案：rclone 同步", "[1.0,0.0,0.0]"),
                row(101L, "购物清单", "[1.0,0.0,0.0]")));

        List<AiKnowledgeService.ScoredChunk> hits = service.searchDetailedKeyword(1L, "图床", 2);

        assertEquals(1, hits.size());
        assertEquals(100L, hits.get(0).chunkId());
    }

    @Test
    void rebuild_并发中_拒绝后到者() {
        ReflectionTestUtils.setField(service, "rebuildLock", new AtomicBoolean(true));
        assertThrows(BusinessException.class, () -> service.rebuild(1L));
    }

    private Map<String, Object> row(Long id, String text, String embedding) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("chunk_text", text);
        m.put("source_name", "src");
        m.put("embedding", embedding);
        return m;
    }

    @Test
    void addDocument_按500字50重叠分块() {
        when(kbMapper.selectById(1L)).thenReturn(kb(1L, null));

        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        doNothing().when(transactionManager).commit(any());

        ArgumentCaptor<AiKnowledgeChunk> cap = ArgumentCaptor.forClass(AiKnowledgeChunk.class);
        when(chunkMapper.insert(cap.capture())).thenReturn(1);
        when(embeddingService.embedBatch(anyList(), any())).thenReturn(new ArrayList<>());
        when(chunkMapper.selectCount(any())).thenReturn(0L);

        // 600 个字符，步长 = 500 - 50 = 450，预期分块 [0,500) [450,950)
        String text = "abcdefghij".repeat(60);
        int n = service.addDocument(1L, text, "src");

        List<AiKnowledgeChunk> inserted = cap.getAllValues();
        assertEquals(inserted.size(), n);
        for (AiKnowledgeChunk c : inserted) {
            assertTrue(c.getChunkText().length() <= 500, "单块不得超过 500 字");
            assertEquals("src", c.getSourceName());
        }
        // 验证 overlap：第二块应以第一块末 50 字开头
        String c0 = inserted.get(0).getChunkText();
        String c1 = inserted.get(1).getChunkText();
        assertTrue(c1.startsWith(c0.substring(c0.length() - 50)), "相邻分块应保留 50 字重叠");
    }
}
