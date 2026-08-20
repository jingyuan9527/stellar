package com.stellar.infra;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.system.entity.SysLog;
import com.stellar.system.service.SysLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ExternalCallLogger} 单测：success/failure 四重载、操作人解析（登录只取 userId/匿名/异常）、
 * 参数与错误信息截断、写 sys_log 异常吞掉。
 * <p>P3 起登录态解析只填 operatorUserId，用户名由 saveLog 异步线程查库填充（本层不再查库）。
 */
@ExtendWith(MockitoExtension.class)
class ExternalCallLoggerTest {

    @Mock
    SysLogService sysLogService;

    private ExternalCallLogger logger() {
        return new ExternalCallLogger(sysLogService);
    }

    @Test
    void success_未传operator_解析匿名() {
        ExternalCallLogger logger = logger();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            logger.success("AI图片", "generate", "p", 100L);
        }
        ArgumentCaptor<SysLog> cap = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(cap.capture());
        SysLog log = cap.getValue();
        assertEquals("外部调用", log.getModule());
        assertEquals("OTHER", log.getOperationType());
        assertEquals("anonymous", log.getOperator());
        assertEquals(1, log.getStatus());
        assertEquals("AI图片 / generate", log.getRequestUrl());
        assertEquals(100L, log.getDuration());
    }

    @Test
    void failure_传operator_记录错误与状态0() {
        ExternalCallLogger logger = logger();
        logger.failure("LLM", "chat", "params", "boom", 200L, "account:1");

        ArgumentCaptor<SysLog> cap = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(cap.capture());
        assertEquals("account:1", cap.getValue().getOperator());
        assertEquals(0, cap.getValue().getStatus());
        assertEquals("boom", cap.getValue().getErrorMsg());
    }

    @Test
    void success_登录_只取userId不查库() {
        ExternalCallLogger logger = logger();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            logger.success("AI图片", "generate", "p", 5L);
        }
        ArgumentCaptor<SysLog> cap = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(cap.capture());
        assertEquals(1L, cap.getValue().getOperatorUserId());
        assertNull(cap.getValue().getOperator(), "用户名应由 saveLog 异步线程解析，本层不填");
    }

    @Test
    void success_登录但用户不存在_记user前缀() {
        ExternalCallLogger logger = logger();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
            logger.success("AI图片", "generate", "p", 5L);
        }
        ArgumentCaptor<SysLog> cap = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(cap.capture());
        assertEquals(9L, cap.getValue().getOperatorUserId());
        assertNull(cap.getValue().getOperator());
    }

    @Test
    void success_解析操作人异常_降级匿名() {
        ExternalCallLogger logger = logger();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenThrow(new RuntimeException("stp down"));
            logger.success("AI图片", "generate", "p", 5L);
        }
        ArgumentCaptor<SysLog> cap = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(cap.capture());
        assertEquals("anonymous", cap.getValue().getOperator());
    }

    @Test
    void record_参数超长_截断() {
        ExternalCallLogger logger = logger();
        String longParams = "x".repeat(3000);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            logger.success("AI图片", "generate", longParams, 5L);
        }
        ArgumentCaptor<SysLog> cap = ArgumentCaptor.forClass(SysLog.class);
        verify(sysLogService).saveLog(cap.capture());
        assertEquals(2000 + 3, cap.getValue().getParams().length());
        assertTrue(cap.getValue().getParams().endsWith("..."));
    }

    @Test
    void record_写日志异常_吞掉不影响主流程() {
        ExternalCallLogger logger = logger();
        doThrow(new RuntimeException("db down")).when(sysLogService).saveLog(any(SysLog.class));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            logger.success("AI图片", "generate", "p", 5L);
        }
        verify(sysLogService).saveLog(any(SysLog.class));
    }
}
