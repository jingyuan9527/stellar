package com.stellar.memos.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.memos.dto.MemosConfigDTO;
import com.stellar.memos.dto.MemosQueryDTO;
import com.stellar.memos.dto.MemosTagDTO;
import com.stellar.memos.service.MemosService;
import com.stellar.memos.vo.MemosConfigVO;
import com.stellar.memos.vo.MemosJobResultVO;
import com.stellar.memos.vo.MemosNoteVO;
import com.stellar.memos.vo.MemosStatsVO;
import com.stellar.memos.vo.MemosSyncLogVO;
import com.stellar.memos.vo.MemosSyncResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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
        when(memosService.syncPullManual()).thenReturn(vo);

        var resp = newController().pull();

        assertEquals(200, resp.getCode());
        assertEquals(3, resp.getData().getFetched());
    }

    @Test
    void syncLogPage_透传分页() {
        Page<MemosSyncLogVO> p = new Page<>(1, 10, 1);
        p.setRecords(List.of(new MemosSyncLogVO()));
        when(memosService.pageSyncLog(any(MemosQueryDTO.class))).thenReturn(p);
        MemosQueryDTO q = new MemosQueryDTO();
        q.setPageNum(1);
        q.setPageSize(10);

        var resp = newController().syncLogPage(q);

        assertEquals(200, resp.getCode());
        assertEquals(1, resp.getData().getRecords().size());
        verify(memosService).pageSyncLog(argThat(dto ->
                dto.getPageNum() == 1 && dto.getPageSize() == 10));
    }

    @Test
    void latestSyncLog_透传最近记录() {
        MemosSyncLogVO vo = new MemosSyncLogVO();
        vo.setStatus("success");
        when(memosService.latestSyncLog()).thenReturn(vo);

        var resp = newController().latestSyncLog();

        assertEquals(200, resp.getCode());
        assertEquals("success", resp.getData().getStatus());
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

    // ===== Webhook =====

    @Test
    void webhook_处理成功_返回code0() {
        byte[] body = "{\"activityType\":\"memos.memo.created\"}".getBytes(StandardCharsets.UTF_8);
        when(memosService.handleWebhook(any(), any(), any(), any()))
                .thenReturn(Map.of("status", "created"));

        var resp = newController().webhook(body, "msg_1", "123", "v1,x");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(0, resp.getBody().get("code"));
    }

    @Test
    void webhook_签名失败_返回400非0code() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        when(memosService.handleWebhook(any(), any(), any(), any()))
                .thenThrow(new BusinessException("Webhook 签名校验失败"));

        var resp = newController().webhook(body, "msg_1", "123", "v1,forged");

        assertEquals(400, resp.getStatusCode().value());
        assertEquals(1, resp.getBody().get("code"));
        assertTrue(resp.getBody().get("message").toString().contains("签名"));
    }

    @Test
    void webhook_未知异常_返回500() {
        when(memosService.handleWebhook(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        var resp = newController().webhook(new byte[]{123}, "msg_1", "123", "v1,x");

        assertEquals(500, resp.getStatusCode().value());
        assertEquals(2, resp.getBody().get("code"));
    }
}
