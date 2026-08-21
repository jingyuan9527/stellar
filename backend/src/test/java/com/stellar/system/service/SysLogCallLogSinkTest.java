package com.stellar.system.service;

import com.stellar.infra.ExternalCallLogEntry;
import com.stellar.system.entity.SysLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SysLogCallLogSink} 单测：ExternalCallLogEntry → sys_log 的字段映射。
 */
@ExtendWith(MockitoExtension.class)
class SysLogCallLogSinkTest {

    @Mock
    SysLogService sysLogService;

    @Test
    void write_完整映射() {
        SysLogCallLogSink sink = new SysLogCallLogSink(sysLogService);
        sink.write(ExternalCallLogEntry.builder()
                .provider("LLM").action("chat").params("p")
                .success(false).errorMsg("boom")
                .durationMs(200L).operator("account:1")
                .operatorUserId(null).ip("1.2.3.4")
                .build());

        ArgumentCaptor<SysLog> cap = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(cap.capture());
        SysLog log = cap.getValue();
        assertEquals("外部调用", log.getModule());
        assertEquals("OTHER", log.getOperationType());
        assertEquals("account:1", log.getOperator());
        assertEquals("POST", log.getRequestMethod());
        assertEquals("LLM / chat", log.getRequestUrl());
        assertEquals("LLM", log.getJavaMethod());
        assertEquals("p", log.getParams());
        assertEquals(0, log.getStatus());
        assertEquals("boom", log.getErrorMsg());
        assertEquals("1.2.3.4", log.getIp());
        assertEquals(200L, log.getDuration());
        assertNotNull(log.getCreateTime());
    }

    @Test
    void write_登录场景_带userId由异步线程解析用户名() {
        SysLogCallLogSink sink = new SysLogCallLogSink(sysLogService);
        sink.write(ExternalCallLogEntry.builder()
                .provider("AI图片").action("generate").params("p")
                .success(true).durationMs(5L)
                .operatorUserId(7L)
                .build());

        ArgumentCaptor<SysLog> cap = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(cap.capture());
        assertEquals(7L, cap.getValue().getOperatorUserId());
        assertNull(cap.getValue().getOperator());
        assertEquals(1, cap.getValue().getStatus());
    }
}
