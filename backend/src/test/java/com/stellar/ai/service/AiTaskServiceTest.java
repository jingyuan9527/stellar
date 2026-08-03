package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiTaskService} 单测：单依赖 aiTaskMapper，覆盖历史落库（含异常吞掉）、
 * 分页/查询、归属校验删除（无权限直接返回不抛）、清空与状态更新。
 */
@ExtendWith(MockitoExtension.class)
class AiTaskServiceTest {

    @Mock
    AiTaskMapper aiTaskMapper;

    AiTaskService service;

    @BeforeEach
    void setup() {
        service = new AiTaskService(aiTaskMapper);
    }

    @Test
    void record_正常_补齐时间并入库() {
        AiTask t = new AiTask();
        service.record(t);
        ArgumentCaptor<AiTask> cap = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskMapper).insert(cap.capture());
        assertNotNull(cap.getValue().getRequestTime());
        assertNotNull(cap.getValue().getCreateTime());
    }

    @Test
    void record_入库异常被吞掉_不抛出() {
        doThrow(new RuntimeException("db down")).when(aiTaskMapper).insert(any(AiTask.class));
        AiTask t = new AiTask();
        assertDoesNotThrow(() -> service.record(t));
    }

    @Test
    void page_正常() {
        when(aiTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(new Page<>());
        assertNotNull(service.page("image", "account", "1", 1, 10));
    }

    @Test
    void getById_正常() {
        when(aiTaskMapper.selectById(1L)).thenReturn(new AiTask());
        assertNotNull(service.getById(1L));
    }

    @Test
    void getFullById_正常() {
        when(aiTaskMapper.selectById(1L)).thenReturn(new AiTask());
        assertNotNull(service.getFullById(1L));
    }

    @Test
    void delete_不存在_直接返回() {
        when(aiTaskMapper.selectById(1L)).thenReturn(null);
        service.delete(1L, "account", "1");
        verify(aiTaskMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_主体不符_直接返回() {
        AiTask t = new AiTask();
        t.setSubjectType("ip");
        t.setSubjectId("9.9.9.9");
        when(aiTaskMapper.selectById(1L)).thenReturn(t);
        service.delete(1L, "account", "1");
        verify(aiTaskMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_正常_删除() {
        AiTask t = new AiTask();
        t.setSubjectType("account");
        t.setSubjectId("1");
        when(aiTaskMapper.selectById(1L)).thenReturn(t);
        service.delete(1L, "account", "1");
        verify(aiTaskMapper).deleteById(1L);
    }

    @Test
    void clear_正常_按条件删() {
        service.clear("video", "ip", "1.1.1.1");
        verify(aiTaskMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void updateStatus_正常_更新字段() {
        service.updateStatus(1L, "completed", 5L, "ok");
        ArgumentCaptor<AiTask> cap = ArgumentCaptor.forClass(AiTask.class);
        verify(aiTaskMapper).updateById(cap.capture());
        assertEquals("completed", cap.getValue().getStatus());
        assertEquals(5L, cap.getValue().getFileId());
        assertEquals("ok", cap.getValue().getErrorMsg());
    }
}
