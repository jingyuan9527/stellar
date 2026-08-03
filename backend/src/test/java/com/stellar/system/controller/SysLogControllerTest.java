package com.stellar.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.system.dto.SysLogQueryDTO;
import com.stellar.system.entity.SysLog;
import com.stellar.system.service.SysLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link SysLogController} 单测：分页/详情透传；export 写 xlsx 到 response 流（用 MockHttpServletResponse
 * 真实验证 Content-Type 头与字节输出），以及 list 失败被吞不抛异常。
 */
@ExtendWith(MockitoExtension.class)
class SysLogControllerTest {

    @Mock
    SysLogService sysLogService;

    SysLogController controller;

    @BeforeEach
    void setup() {
        controller = new SysLogController(sysLogService);
    }

    @Test
    void page_正常() {
        SysLogQueryDTO q = new SysLogQueryDTO();
        when(sysLogService.page(q)).thenReturn(new Page<SysLog>());
        assertNotNull(controller.page(q).getData());
    }

    @Test
    void detail_正常() {
        SysLog log = new SysLog();
        log.setId(3L);
        when(sysLogService.getById(3L)).thenReturn(log);
        assertEquals(3L, controller.detail(3L).getData().getId());
    }

    @Test
    void export_正常_写出xlsx() throws Exception {
        SysLog log = new SysLog();
        log.setId(1L);
        log.setModule("AI对话");
        log.setOperationType("OTHER");
        log.setOperator("admin");
        log.setStatus(1);
        log.setCreateTime(LocalDateTime.of(2026, 8, 2, 10, 0));
        when(sysLogService.list(any(SysLogQueryDTO.class))).thenReturn(List.of(log));

        MockHttpServletResponse response = new MockHttpServletResponse();
        SysLogQueryDTO q = new SysLogQueryDTO();
        controller.export(q, response);

        assertTrue(response.getHeader("Content-Disposition").contains("xlsx"));
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void export_list失败_吞异常不抛出() throws Exception {
        when(sysLogService.list(any(SysLogQueryDTO.class))).thenThrow(new RuntimeException("db down"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        // 不抛异常即通过
        controller.export(new SysLogQueryDTO(), response);
    }
}
