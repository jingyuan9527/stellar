package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.system.dto.SysLogQueryDTO;
import com.stellar.system.entity.SysLog;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysLogMapper;
import com.stellar.system.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SysLogService} 单测：saveLog 正常与异常吞掉、operatorUserId 异步解析用户名、page/getById/list。
 */
@ExtendWith(MockitoExtension.class)
class SysLogServiceTest {

    @Mock
    SysLogMapper sysLogMapper;
    @Mock
    SysUserMapper sysUserMapper;

    private SysLogService serviceWithMapper() {
        SysLogService service = new SysLogService(sysLogMapper);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
        return service;
    }

    @Test
    void saveLog_正常_插入() {
        SysLogService service = new SysLogService(sysLogMapper);
        SysLog log = new SysLog();
        service.saveLog(log);
        verify(sysLogMapper).insert(log);
    }

    @Test
    void saveLog_插入异常_吞掉() {
        SysLogService service = new SysLogService(sysLogMapper);
        doThrow(new RuntimeException("db down")).when(sysLogMapper).insert(any(SysLog.class));
        service.saveLog(new SysLog());
        verify(sysLogMapper).insert(any(SysLog.class));
    }

    @Test
    void saveLog_operatorUserId_解析用户名() {
        SysLogService service = serviceWithMapper();
        SysLog log = new SysLog();
        log.setOperatorUserId(5L);
        SysUser u = new SysUser();
        u.setId(5L);
        u.setUsername("alice");
        when(sysUserMapper.selectById(5L)).thenReturn(u);

        service.saveLog(log);

        assertEquals("alice", log.getOperator());
        verify(sysLogMapper).insert(log);
    }

    @Test
    void saveLog_operatorUserId_用户不存在_记user前缀() {
        SysLogService service = serviceWithMapper();
        SysLog log = new SysLog();
        log.setOperatorUserId(9L);
        when(sysUserMapper.selectById(9L)).thenReturn(null);

        service.saveLog(log);

        assertEquals("user:9", log.getOperator());
        verify(sysLogMapper).insert(log);
    }

    @Test
    void saveLog_operator非空_不覆盖() {
        SysLogService service = serviceWithMapper();
        SysLog log = new SysLog();
        log.setOperator("admin");
        log.setOperatorUserId(5L);

        service.saveLog(log);

        assertEquals("admin", log.getOperator());
        verify(sysUserMapper, never()).selectById(any());
        verify(sysLogMapper).insert(log);
    }

    @Test
    void saveLog_解析异常_保留空operator不阻断落库() {
        SysLogService service = serviceWithMapper();
        SysLog log = new SysLog();
        log.setOperatorUserId(5L);
        when(sysUserMapper.selectById(5L)).thenThrow(new RuntimeException("db down"));

        service.saveLog(log);

        assertNull(log.getOperator());
        verify(sysLogMapper).insert(log);
    }

    @Test
    void page_构建分页查询() {
        SysLogService service = new SysLogService(sysLogMapper);
        Page<SysLog> page = new Page<>(1, 10);
        when(sysLogMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        SysLogQueryDTO q = new SysLogQueryDTO();
        Page<SysLog> result = service.page(q);
        assertEquals(1, result.getCurrent());
        verify(sysLogMapper).selectPage(any(Page.class), any(Wrapper.class));
    }

    @Test
    void getById_返回() {
        SysLogService service = new SysLogService(sysLogMapper);
        SysLog log = new SysLog();
        log.setId(1L);
        when(sysLogMapper.selectById(1L)).thenReturn(log);
        assertEquals(1L, service.getById(1L).getId());
    }

    @Test
    void list_构建查询() {
        SysLogService service = new SysLogService(sysLogMapper);
        when(sysLogMapper.selectList(any(Wrapper.class))).thenReturn(List.of(new SysLog()));
        SysLogQueryDTO q = new SysLogQueryDTO();
        assertEquals(1, service.list(q).size());
    }
}
