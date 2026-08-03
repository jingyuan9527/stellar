package com.stellar.memos.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.memos.dto.MemosConfigDTO;
import com.stellar.memos.dto.MemosQueryDTO;
import com.stellar.memos.dto.MemosTagDTO;
import com.stellar.memos.service.MemosService;
import com.stellar.memos.vo.MemosConfigVO;
import com.stellar.memos.vo.MemosJobResultVO;
import com.stellar.memos.vo.MemosNoteVO;
import com.stellar.memos.vo.MemosStatsVO;
import com.stellar.memos.vo.MemosSyncResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link MemosController} 单测：纯 Mockito，构造注入 Service，验证各接口透传与分页参数。
 */
@ExtendWith(MockitoExtension.class)
class MemosControllerTest {

    @Mock
    private MemosService memosService;

    private MemosController newController() {
        return new MemosController(memosService);
    }

    @Test
    void getConfig_透传VO() {
        MemosConfigVO vo = new MemosConfigVO();
        vo.setBaseUrl("https://memo.booksy.cf");
        when(memosService.getConfig()).thenReturn(vo);

        var resp = newController().getConfig();

        assertEquals(200, resp.getCode());
        assertSame(vo, resp.getData());
    }

    @Test
    void saveConfig_透传DTO() {
        MemosConfigDTO dto = new MemosConfigDTO();
        dto.setBaseUrl("https://memo.booksy.cf");
        dto.setToken("tok");

        var resp = newController().saveConfig(dto);

        assertEquals(200, resp.getCode());
        verify(memosService).saveConfig(dto);
    }

    @Test
    void pull_透传同步结果() {
        MemosSyncResultVO vo = new MemosSyncResultVO();
        vo.setFetched(3);
        when(memosService.syncPull()).thenReturn(vo);

        var resp = newController().pull();

        assertEquals(200, resp.getCode());
        assertEquals(3, resp.getData().getFetched());
    }

    @Test
    void tag_勾选ids与modelId透传任务结果() {
        MemosJobResultVO vo = new MemosJobResultVO();
        vo.setProcessed(2);
        when(memosService.aiTag(List.of(1L, 2L), 5L)).thenReturn(vo);
        MemosTagDTO dto = new MemosTagDTO();
        dto.setIds(List.of(1L, 2L));
        dto.setModelId(5L);

        var resp = newController().tag(dto);

        assertEquals(200, resp.getCode());
        assertEquals(2, resp.getData().getProcessed());
    }

    @Test
    void pushTags_透传任务结果() {
        MemosJobResultVO vo = new MemosJobResultVO();
        vo.setSuccess(1);
        when(memosService.pushTags()).thenReturn(vo);

        var resp = newController().pushTags();

        assertEquals(200, resp.getCode());
        assertEquals(1, resp.getData().getSuccess());
    }

    @Test
    void page_透传查询参数() {
        Page<MemosNoteVO> p = new Page<>(1, 10, 1);
        when(memosService.page(any(MemosQueryDTO.class))).thenReturn(p);
        MemosQueryDTO q = new MemosQueryDTO();
        q.setPageNum(1);
        q.setPageSize(10);
        q.setKeyword("k");
        q.setRemoteDeleted(0);

        var resp = newController().page(q);

        assertEquals(200, resp.getCode());
        verify(memosService).page(argThat(dto ->
                dto.getPageNum() == 1 && dto.getPageSize() == 10
                        && "k".equals(dto.getKeyword()) && dto.getRemoteDeleted() == 0));
    }

    @Test
    void stats_透传统计() {
        MemosStatsVO vo = new MemosStatsVO();
        vo.setTotal(10L);
        when(memosService.stats()).thenReturn(vo);

        var resp = newController().stats();

        assertEquals(200, resp.getCode());
        assertEquals(10L, resp.getData().getTotal());
    }
}
