package com.stellar.memos.service;

import com.stellar.ai.service.AiEmbeddingService;
import com.stellar.memos.entity.MemosNote;
import com.stellar.memos.mapper.MemosNoteMapper;
import com.stellar.memos.vo.MemosJobResultVO;
import com.stellar.system.service.SysSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemosRagService} 单测：搜索余弦 top-k 排序 + 来源链接拼装、rebuild 批量向量化统计。
 * 纯 Mockito 隔离 mapper/embeddingService/jdbcTemplate/sysSettingService。
 */
class MemosRagServiceTest {

    private MemosNoteMapper memosNoteMapper;
    private AiEmbeddingService embeddingService;
    private JdbcTemplate jdbcTemplate;
    private SysSettingService sysSettingService;
    private MemosRagService service;

    @BeforeEach
    void setUp() {
        memosNoteMapper = mock(MemosNoteMapper.class);
        embeddingService = mock(AiEmbeddingService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        sysSettingService = mock(SysSettingService.class);
        service = new MemosRagService(memosNoteMapper, embeddingService, jdbcTemplate, sysSettingService);
        when(sysSettingService.get(MemosService.KEY_BASE_URL, "")).thenReturn("https://memo.booksy.cf");
    }

    private Map<String, Object> row(Long id, String uid, String content, String emb) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("uid", uid);
        m.put("content", content);
        m.put("embedding", emb);
        return m;
    }

    @Test
    void search_余弦排序并拼装来源链接() {
        when(embeddingService.embed(eq("q"), eq(null))).thenReturn(new float[]{1f, 0f, 0f});
        when(jdbcTemplate.queryForList(anyString()))
                .thenReturn(List.of(
                        row(1L, "u-a", "标题A\n正文A", "[1.0,0.0,0.0]"),
                        row(2L, "u-b", "标题B", "[0.6,0.8,0.0,0.0]"),
                        row(3L, "u-c", "无向量", null)));

        List<MemosRagService.MemoHit> hits = service.search("q", 2);

        assertEquals(2, hits.size());
        assertEquals("u-a", hits.get(0).uid());
        assertEquals("https://memo.booksy.cf/memos/u-a", hits.get(0).url());
        assertEquals("标题A", hits.get(0).title());
        assertEquals("u-b", hits.get(1).uid());
        assertTrue(hits.get(0).score() > hits.get(1).score());
    }

    @Test
    void search_查询向量化失败_返回空() {
        when(embeddingService.embed(any(), any())).thenThrow(new RuntimeException("boom"));
        assertTrue(service.search("q", 4).isEmpty());
    }

    @Test
    void rebuildAll_批量向量化并回填() {
        MemosNote n1 = new MemosNote();
        n1.setId(1L);
        n1.setContent("n1");
        n1.setRemoteDeleted(0);
        MemosNote n2 = new MemosNote();
        n2.setId(2L);
        n2.setContent("n2");
        n2.setRemoteDeleted(0);
        when(memosNoteMapper.selectList(any())).thenReturn(List.of(n1, n2));
        when(embeddingService.embedBatch(any(), any())).thenReturn(List.of(new float[]{1f}, new float[]{2f}));

        MemosJobResultVO vo = service.rebuildAll();

        assertEquals(2, vo.getProcessed());
        assertEquals(2, vo.getSuccess());
        assertEquals(0, vo.getFailed());
        verify(jdbcTemplate).update(eq("UPDATE memos_note SET embedding = ? WHERE id = ?"), any(), eq(1L));
    }

    @Test
    void rebuildAll_无存活笔记_返回0() {
        when(memosNoteMapper.selectList(any())).thenReturn(List.of());
        MemosJobResultVO vo = service.rebuildAll();
        assertEquals(0, vo.getProcessed());
        verify(embeddingService, never()).embedBatch(any(), any());
    }

    @Test
    void embedNoteAsync_单条向量化并清缓存() {
        when(embeddingService.embed(eq("内容"), eq(null))).thenReturn(new float[]{1f, 2f});
        service.embedNoteAsync(9L, "内容");
        verify(jdbcTemplate).update(eq("UPDATE memos_note SET embedding = ? WHERE id = ?"),
                eq("[1.0,2.0]"), eq(9L));
    }

    @Test
    void embedNoteAsync_空内容_不调embedding() {
        service.embedNoteAsync(9L, "  ");
        verify(embeddingService, never()).embed(any(), any());
    }
}
