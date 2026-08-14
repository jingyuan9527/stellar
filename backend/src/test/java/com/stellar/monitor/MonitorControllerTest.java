package com.stellar.monitor;

import com.stellar.common.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link MonitorController} 单测：overview 透传、export 响应头与 Markdown 内容。
 */
@ExtendWith(MockitoExtension.class)
class MonitorControllerTest {

    @Mock
    MonitorService monitorService;

    MonitorController controller;

    @BeforeEach
    void setup() {
        controller = new MonitorController(monitorService);
    }

    @Test
    void overview_正常() {
        MonitorOverviewVO vo = new MonitorOverviewVO();
        when(monitorService.overview()).thenReturn(vo);
        Result<MonitorOverviewVO> result = controller.overview();
        assertSame(vo, result.getData());
    }

    @Test
    void export_返回Markdown与附件头() {
        when(monitorService.exportMarkdown()).thenReturn("# Stellar 系统监控报告\n## JVM\n");
        ResponseEntity<byte[]> resp = controller.export();
        assertTrue(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE).startsWith("text/markdown"));
        String disposition = resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition != null && disposition.contains("attachment; filename=\"stellar-monitor-"));
        assertTrue(disposition.endsWith(".md\""));
        assertEquals("# Stellar 系统监控报告", new String(resp.getBody(), StandardCharsets.UTF_8).split("\n")[0]);
    }
}