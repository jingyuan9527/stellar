package com.stellar.system.controller;

import com.stellar.system.dto.ClientErrorReportDTO;
import com.stellar.system.entity.SysLog;
import com.stellar.system.service.SysLogService;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ClientErrorController} 单测：前端错误上报落 sys_log（module=前端错误），IP 取自请求。
 */
@ExtendWith(MockitoExtension.class)
class ClientErrorControllerTest {

    @Mock
    SysLogService sysLogService;
    @Mock
    HttpServletRequest httpRequest;

    ClientErrorController controller;

    @BeforeEach
    void setup() {
        controller = new ClientErrorController(sysLogService);
        // lenient：report_url为空 用例传独立 mock request，不使用本字段 stub
        lenient().when(httpRequest.getRemoteAddr()).thenReturn("1.2.3.4");
    }

    @Test
    void report_成功_落库模块为前端错误() {
        ClientErrorReportDTO dto = new ClientErrorReportDTO();
        dto.setMessage("Cannot read properties of undefined");
        dto.setStack("at render (App.vue:1:1)");
        dto.setSource("vue");
        dto.setUrl("/memos");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            assertEquals(200, controller.report(dto, httpRequest).getCode());
        }

        ArgumentCaptor<SysLog> captor = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(captor.capture());
        SysLog saved = captor.getValue();
        assertEquals("前端错误", saved.getModule());
        assertEquals(dto.getMessage(), saved.getParams());
        assertEquals(dto.getStack(), saved.getErrorMsg());
        assertEquals("1.2.3.4", saved.getIp());
        assertEquals(dto.getUrl(), saved.getRequestUrl());
    }

    @Test
    void report_url为空_请求URL记为占位() {
        ClientErrorReportDTO dto = new ClientErrorReportDTO();
        dto.setMessage("boom");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            controller.report(dto, mock(HttpServletRequest.class));
        }

        ArgumentCaptor<SysLog> captor = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(captor.capture());
        assertEquals("-", captor.getValue().getRequestUrl());
    }
}