package com.stellar.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.dto.AiImageHistoryQueryDTO;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.ai.service.AiImageService;
import com.stellar.ai.service.AiImageTaskWorker;
import com.stellar.ai.service.AiModelService;
import com.stellar.ai.service.SysAiUsageService;
import com.stellar.ai.vo.AiImageTaskVO;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.common.BusinessException;
import com.stellar.system.entity.SysFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiImageService} 单测：用构造注入隔离 6 个协作者（含真实 ObjectMapper 以解析 extra JSON），
 * 覆盖任务查询/删除的校验分支、历史分页、createTask 的模型类型校验与异步派发、以及
 * generateImageSync 的成功落库与失败回写。getSubjectType/Id 依赖 StpUtil，用 MockedStatic 模拟登录态。
 */
@ExtendWith(MockitoExtension.class)
class AiImageServiceTest {

    @Mock
    AiModelService aiModelService;
    @Mock
    AiTaskMapper aiTaskMapper;
    @Mock
    com.stellar.system.service.FileService fileService;
    @Mock
    AiImageTaskWorker worker;
    @Mock
    SysAiUsageService sysAiUsageService;

    AiImageService service;

    @BeforeEach
    void setup() {
        service = new AiImageService(aiModelService, aiTaskMapper, fileService, worker, sysAiUsageService, new ObjectMapper());
    }

    private AiTask task(Long id, String type, String subjectType, String subjectId, String status, Long fileId, String extra) {
        AiTask t = new AiTask();
        t.setId(id);
        t.setTaskType(type);
        t.setSubjectType(subjectType);
        t.setSubjectId(subjectId);
        t.setStatus(status);
        t.setFileId(fileId);
        t.setExtra(extra);
        t.setPrompt("p");
        t.setCreateTime(LocalDateTime.now());
        t.setUpdateTime(LocalDateTime.now());
        return t;
    }

    private AiResolvedConfig imageCfg() {
        return new AiResolvedConfig(1L, 1L, "https://ep", "key", "m", "IMAGE");
    }

    // ===== getTask =====

    @Test
    void getTask_不存在_抛() {
        when(aiTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getTask(1L));
    }

    @Test
    void getTask_正常_解析url与extra() {
        when(aiTaskMapper.selectById(1L))
                .thenReturn(task(1L, "image", "account", "u1", "completed", 9L, "{\"size\":\"1K\",\"ratio\":\"1:1\"}"));
        AiImageTaskVO vo = service.getTask(1L);
        assertEquals("/file/9", vo.getUrl());
        assertEquals("1K", vo.getSize());
        assertEquals("1:1", vo.getRatio());
        assertEquals("completed", vo.getStatus());
    }

    // ===== deleteTask =====

    @Test
    void deleteTask_不存在_抛() {
        when(aiTaskMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.deleteTask(1L, "account", "u1"));
    }

    @Test
    void deleteTask_无权_抛() {
        when(aiTaskMapper.selectById(1L)).thenReturn(task(1L, "image", "account", "u1", "completed", 9L, null));
        assertThrows(BusinessException.class, () -> service.deleteTask(1L, "ip", "1.2.3.4"));
    }

    @Test
    void deleteTask_正常_删关联文件() {
        when(aiTaskMapper.selectById(1L)).thenReturn(task(1L, "image", "account", "u1", "completed", 9L, null));
        service.deleteTask(1L, "account", "u1");
        verify(fileService).deleteById(9L);
        verify(aiTaskMapper).deleteById(1L);
    }

    // ===== pageHistory =====

    @Test
    void pageHistory_正常() {
        AiTask t = task(1L, "image", "account", "u1", "completed", 9L, null);
        Page<AiTask> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(t));
        when(aiTaskMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        AiImageHistoryQueryDTO q = new AiImageHistoryQueryDTO();
        q.setPageNum(1);
        q.setPageSize(10);
        Page<AiImageTaskVO> r = service.pageHistory(q, "account", "u1");
        assertEquals(1, r.getRecords().size());
        assertEquals("completed", r.getRecords().get(0).getStatus());
    }

    // ===== createTask =====

    @Test
    void createTask_非IMAGE模型_抛() {
        when(aiModelService.resolveConfig(2L))
                .thenReturn(new AiResolvedConfig(1L, 1L, "ep", "key", "m", "TEXT"));
        assertThrows(BusinessException.class, () -> service.createTask(2L, "p", "1K", "1:1"));
    }

    @Test
    void createTask_正常_派发异步() {
        when(aiModelService.resolveConfig(2L)).thenReturn(imageCfg());
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsString).thenReturn("u1");
        service.createTask(2L, "p", "1K", "1:1");
            verify(aiTaskMapper).insert(any(AiTask.class));
            verify(worker).doGenerateAsync(any());
        }
    }

    // ===== generateImageSync =====

    @Test
    void generateImageSync_成功_落库并记usage() throws Exception {
        when(aiModelService.resolveDefaultOrFirstEnabled("IMAGE")).thenReturn(imageCfg());
        when(worker.generateImageBytes(any(), anyString(), anyString(), anyString())).thenReturn(new byte[]{1, 2, 3});
        service.generateImageSync("p", "account", "u1");
        verify(aiTaskMapper).insert(any(AiTask.class));
        verify(fileService).create(any(SysFile.class));
        verify(sysAiUsageService).record(eq("account"), eq("u1"), anyLong(), anyString(), eq("IMAGE"), anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    void generateImageSync_失败_回写failed并抛() throws Exception {
        when(aiModelService.resolveDefaultOrFirstEnabled("IMAGE")).thenReturn(imageCfg());
        when(worker.generateImageBytes(any(), anyString(), anyString(), anyString()))
                .thenThrow(new BusinessException("生成失败"));
        assertThrows(BusinessException.class, () -> service.generateImageSync("p", "account", "u1"));
        // 失败路径：任务先创建（status=generating），catch 内 setStatus(failed)+updateById 回写
        verify(aiTaskMapper).insert(any(AiTask.class));
    }
}
