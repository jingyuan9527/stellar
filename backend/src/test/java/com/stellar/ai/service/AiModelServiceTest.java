package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.ai.dto.AiModelDTO;
import com.stellar.ai.entity.SysAiModel;
import com.stellar.ai.entity.SysAiProvider;
import com.stellar.ai.mapper.SysAiModelMapper;
import com.stellar.ai.mapper.SysAiProviderMapper;
import com.stellar.ai.vo.AiModelVO;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiModelService} 单测：构造注入两个 Mapper，覆盖配置解析（resolveConfig /
 * resolveDefaultConfig / resolveDefaultOrFirstEnabled）的多分支校验、CRUD 校验与
 * 同类型默认互斥（setDefault）、列表查询的供应商名联查与启用过滤。
 */
@ExtendWith(MockitoExtension.class)
class AiModelServiceTest {

    @Mock
    SysAiModelMapper modelMapper;
    @Mock
    SysAiProviderMapper providerMapper;

    AiModelService service;

    @BeforeEach
    void setup() {
        service = new AiModelService(modelMapper, providerMapper);
    }

    private SysAiModel model(Long id, Long pid, String name, String type, Integer enabled, Integer isDefault) {
        SysAiModel m = new SysAiModel();
        m.setId(id);
        m.setProviderId(pid);
        m.setModel(name);
        m.setModelType(type);
        m.setEnabled(enabled);
        m.setIsDefault(isDefault);
        return m;
    }

    private SysAiProvider provider(Long id, String name, String ep, String key, Integer enabled) {
        SysAiProvider p = new SysAiProvider();
        p.setId(id);
        p.setName(name);
        p.setEndpoint(ep);
        p.setApiKey(key);
        p.setEnabled(enabled);
        return p;
    }

    // ===== resolveConfig 配置解析校验 =====

    @Test
    void resolveConfig_正常() {
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "gpt", "TEXT", 1, 0));
        when(providerMapper.selectById(10L)).thenReturn(provider(10L, "p", "https://ep", "key", 1));
        AiResolvedConfig c = service.resolveConfig(1L);
        assertEquals("https://ep", c.endpoint());
        assertEquals("key", c.apiKey());
        assertEquals("gpt", c.model());
        assertEquals("TEXT", c.modelType());
        assertEquals(10L, c.providerId());
    }

    @Test
    void resolveConfig_模型不存在() {
        when(modelMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.resolveConfig(1L));
    }

    @Test
    void resolveConfig_模型禁用() {
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "gpt", "TEXT", 0, 0));
        assertThrows(BusinessException.class, () -> service.resolveConfig(1L));
    }

    @Test
    void resolveConfig_供应商不存在() {
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "gpt", "TEXT", 1, 0));
        when(providerMapper.selectById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.resolveConfig(1L));
    }

    @Test
    void resolveConfig_供应商禁用() {
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "gpt", "TEXT", 1, 0));
        when(providerMapper.selectById(10L)).thenReturn(provider(10L, "p", "ep", "key", 0));
        assertThrows(BusinessException.class, () -> service.resolveConfig(1L));
    }

    @Test
    void resolveConfig_无Endpoint() {
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "gpt", "TEXT", 1, 0));
        when(providerMapper.selectById(10L)).thenReturn(provider(10L, "p", "", "key", 1));
        assertThrows(BusinessException.class, () -> service.resolveConfig(1L));
    }

    @Test
    void resolveConfig_无ApiKey() {
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "gpt", "TEXT", 1, 0));
        when(providerMapper.selectById(10L)).thenReturn(provider(10L, "p", "ep", "", 1));
        assertThrows(BusinessException.class, () -> service.resolveConfig(1L));
    }

    // ===== resolveDefaultConfig / resolveDefaultOrFirstEnabled =====

    @Test
    void resolveDefaultConfig_无默认_抛() {
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.resolveDefaultConfig("TEXT"));
    }

    @Test
    void resolveDefaultConfig_有默认() {
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(model(1L, 10L, "gpt", "TEXT", 1, 1));
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "gpt", "TEXT", 1, 1));
        when(providerMapper.selectById(10L)).thenReturn(provider(10L, "p", "ep", "key", 1));
        AiResolvedConfig c = service.resolveDefaultConfig("TEXT");
        assertEquals("gpt", c.model());
    }

    @Test
    void resolveDefaultOrFirstEnabled_默认成功() {
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(model(1L, 10L, "gpt", "TEXT", 1, 1));
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "gpt", "TEXT", 1, 1));
        when(providerMapper.selectById(10L)).thenReturn(provider(10L, "p", "ep", "key", 1));
        AiResolvedConfig c = service.resolveDefaultOrFirstEnabled("TEXT");
        assertEquals("gpt", c.model());
    }

    @Test
    void resolveDefaultOrFirstEnabled_默认无_兜底第一个启用() {
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(modelMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(model(5L, 10L, "gpt2", "TEXT", 1, 0)));
        when(providerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(provider(10L, "p", "ep", "key", 1)));
        when(modelMapper.selectById(5L)).thenReturn(model(5L, 10L, "gpt2", "TEXT", 1, 0));
        when(providerMapper.selectById(10L)).thenReturn(provider(10L, "p", "ep", "key", 1));
        AiResolvedConfig c = service.resolveDefaultOrFirstEnabled("TEXT");
        assertEquals("gpt2", c.model());
    }

    @Test
    void resolveDefaultOrFirstEnabled_都无_抛() {
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(modelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.resolveDefaultOrFirstEnabled("TEXT"));
    }

    // ===== create / update / toggle / setDefault 校验与互斥 =====

    @Test
    void create_供应商空_抛() {
        AiModelDTO dto = new AiModelDTO();
        assertThrows(BusinessException.class, () -> service.create(dto));
    }

    @Test
    void create_供应商不存在_抛() {
        AiModelDTO dto = new AiModelDTO();
        dto.setProviderId(10L);
        when(providerMapper.selectById(10L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.create(dto));
    }

    @Test
    void create_正常_不设默认() {
        AiModelDTO dto = new AiModelDTO();
        dto.setProviderId(10L);
        dto.setModel(" gpt ");
        dto.setModelType(" TEXT ");
        dto.setEnabled(1);
        dto.setIsDefault(0);
        dto.setSortOrder(0);
        when(providerMapper.selectById(10L)).thenReturn(provider(10L, "p", "ep", "key", 1));
        service.create(dto);
        verify(modelMapper).insert(any(SysAiModel.class));
        verify(modelMapper, never()).updateById((SysAiModel) any());
    }

    @Test
    void update_id空_抛() {
        AiModelDTO dto = new AiModelDTO();
        assertThrows(BusinessException.class, () -> service.update(dto));
    }

    @Test
    void update_不存在_抛() {
        AiModelDTO dto = new AiModelDTO();
        dto.setId(1L);
        when(modelMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.update(dto));
    }

    @Test
    void update_正常() {
        AiModelDTO dto = new AiModelDTO();
        dto.setId(1L);
        dto.setModel("x");
        dto.setModelType("y");
        dto.setEnabled(1);
        dto.setSortOrder(0);
        dto.setIsDefault(0);
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "old", "old", 1, 0));
        service.update(dto);
        // isDefault=0 时除主体更新外还会再 updateById 清默认，共 2 次
        verify(modelMapper, times(2)).updateById((SysAiModel) any());
    }

    @Test
    void toggleEnabled_不存在_抛() {
        when(modelMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.toggleEnabled(1L, 1));
    }

    @Test
    void toggleEnabled_正常() {
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "m", "t", 1, 0));
        service.toggleEnabled(1L, 0);
        verify(modelMapper).updateById((SysAiModel) any());
    }

    @Test
    void setDefault_不存在_抛() {
        when(modelMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.setDefault(1L));
    }

    @Test
    void setDefault_正常_同类型互斥() {
        when(modelMapper.selectById(1L)).thenReturn(model(1L, 10L, "m", "TEXT", 1, 0));
        service.setDefault(1L);
        verify(modelMapper).update(any(SysAiModel.class), any(LambdaQueryWrapper.class));
        verify(modelMapper).updateById((SysAiModel) any());
    }

    // ===== 列表查询：供应商名联查 / 启用过滤 =====

    @Test
    void listByProvider_正常() {
        when(modelMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(model(1L, 10L, "m", "TEXT", 1, 0)));
        List<AiModelVO> r = service.listByProvider(10L);
        assertEquals(1, r.size());
        assertEquals("m", r.get(0).getModel());
    }

    @Test
    void listAll_空_返回空() {
        when(modelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertTrue(service.listAll().isEmpty());
    }

    @Test
    void listAll_正常_带供应商名() {
        when(modelMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(model(1L, 10L, "m", "TEXT", 1, 0)));
        when(providerMapper.selectBatchIds(any())).thenReturn(List.of(provider(10L, "p", "ep", "key", 1)));
        List<AiModelVO> r = service.listAll();
        assertEquals(1, r.size());
        assertEquals("p", r.get(0).getProviderName());
    }

    @Test
    void listEnabledByType_仅保留启用供应商模型() {
        when(modelMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        model(1L, 10L, "m", "TEXT", 1, 0),
                        model(2L, 11L, "m2", "TEXT", 1, 0)));
        when(providerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(provider(10L, "p", "ep", "key", 1)));
        List<AiModelVO> r = service.listEnabledByType("TEXT");
        assertEquals(1, r.size());
        assertEquals("m", r.get(0).getModel());
    }

    @Test
    void listEnabledByType_空_返回空() {
        when(modelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertTrue(service.listEnabledByType("TEXT").isEmpty());
    }
}
