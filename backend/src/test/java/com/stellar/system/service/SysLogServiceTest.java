package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.system.dto.SysLogQueryDTO;
import com.stellar.system.entity.SysLog;
import com.stellar.system.mapper.SysLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SysLogService} 单测：saveLog 正常与异常吞掉、page/getById/list。
 */
@ExtendWith(MockitoExtension.class)
class SysLogServiceTest {

    @Mock
    SysLogMapper sysLogMapper;

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
