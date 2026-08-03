package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.AiBuiltinTemplates;
import com.stellar.ai.dto.AiTemplateDTO;
import com.stellar.ai.dto.AiTemplateQueryDTO;
import com.stellar.ai.entity.SysAiTemplate;
import com.stellar.ai.mapper.SysAiTemplateMapper;
import com.stellar.common.BusinessException;
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
 * {@link AiTemplateService} 单测：单依赖 templateMapper，覆盖分页（含条件）、增改删 CRUD
 * 校验，以及内置模板恢复默认（成功 + 非内置抛错）。
 */
@ExtendWith(MockitoExtension.class)
class AiTemplateServiceTest {

    @Mock
    SysAiTemplateMapper templateMapper;

    AiTemplateService service;

    @BeforeEach
    void setup() {
        service = new AiTemplateService(templateMapper);
    }

    private AiTemplateDTO dto() {
        AiTemplateDTO d = new AiTemplateDTO();
        d.setName("名");
        d.setPlatform("bilibili");
        d.setPrompt("提示词");
        return d;
    }

    private SysAiTemplate template(Long id, Integer builtIn, String platform) {
        SysAiTemplate t = new SysAiTemplate();
        t.setId(id);
        t.setBuiltIn(builtIn);
        t.setPlatform(platform);
        return t;
    }

    @Test
    void page_带名称与平台条件() {
        AiTemplateQueryDTO q = new AiTemplateQueryDTO();
        q.setName("x");
        q.setPlatform("bilibili");
        when(templateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(new Page<>());
        assertNotNull(service.page(q));
    }

    @Test
    void page_仅名称条件() {
        AiTemplateQueryDTO q = new AiTemplateQueryDTO();
        q.setName("y");
        when(templateMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(new Page<>());
        assertNotNull(service.page(q));
    }

    @Test
    void create_正常_强制内置为0并记录创建者() {
        service.create(dto(), 7L);
        ArgumentCaptor<SysAiTemplate> cap = ArgumentCaptor.forClass(SysAiTemplate.class);
        verify(templateMapper).insert(cap.capture());
        SysAiTemplate t = cap.getValue();
        assertEquals(0, t.getBuiltIn());
        assertEquals(7L, t.getCreatorId());
    }

    @Test
    void update_不存在_抛() {
        when(templateMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.update(1L, dto()));
    }

    @Test
    void update_正常() {
        when(templateMapper.selectById(1L)).thenReturn(template(1L, 0, "bilibili"));
        service.update(1L, dto());
        verify(templateMapper).updateById(any(SysAiTemplate.class));
    }

    @Test
    void delete_不存在_抛() {
        when(templateMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    void delete_内置不可删_抛() {
        when(templateMapper.selectById(1L)).thenReturn(template(1L, 1, "bilibili"));
        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    void delete_正常() {
        when(templateMapper.selectById(1L)).thenReturn(template(1L, 0, "bilibili"));
        service.delete(1L);
        verify(templateMapper).deleteById(1L);
    }

    @Test
    void resetBuiltin_不存在_抛() {
        when(templateMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.resetBuiltin(1L));
    }

    @Test
    void resetBuiltin_非内置_抛() {
        when(templateMapper.selectById(1L)).thenReturn(template(1L, 0, "bilibili"));
        assertThrows(BusinessException.class, () -> service.resetBuiltin(1L));
    }

    @Test
    void resetBuiltin_成功_恢复种子配置() {
        when(templateMapper.selectById(1L)).thenReturn(template(1L, 1, "xiaohongshu"));
        service.resetBuiltin(1L);
        ArgumentCaptor<SysAiTemplate> cap = ArgumentCaptor.forClass(SysAiTemplate.class);
        verify(templateMapper).updateById(cap.capture());
        AiBuiltinTemplates.Seed seed = AiBuiltinTemplates.findByKey("xiaohongshu");
        assertEquals(seed.name(), cap.getValue().getName());
        assertEquals(seed.prompt(), cap.getValue().getPrompt());
    }
}
