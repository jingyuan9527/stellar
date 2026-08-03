package com.stellar.ai.service;

import com.stellar.ai.entity.AiKnowledgeBase;
import com.stellar.ai.entity.AiKnowledgeChunk;
import com.stellar.ai.mapper.AiKnowledgeBaseMapper;
import com.stellar.ai.mapper.AiKnowledgeChunkMapper;
import com.stellar.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
