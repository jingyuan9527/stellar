package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.dto.AiProviderDTO;
import com.stellar.ai.entity.SysAiModel;
import com.stellar.ai.entity.SysAiProvider;
import com.stellar.ai.mapper.SysAiModelMapper;
import com.stellar.ai.mapper.SysAiProviderMapper;
import com.stellar.ai.vo.AiProviderVO;
import com.stellar.common.BusinessException;
import com.stellar.test.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiProviderService} 单测：覆盖列表脱敏（toVO / maskApiKey）、raw 取数校验、
 * CRUD 校验（含 apiKey 为空保留原值、删除级联模型）、previewModels / saveSelectedModels /
 * clearModels / testConnection 前置校验分支，以及注入 mock HttpClient 覆盖 HTTP 成功/失败路径。
 */
@ExtendWith(MockitoExtension.class)
class AiProviderServiceTest {

    @Mock
    SysAiProviderMapper providerMapper;
    @Mock
    SysAiModelMapper modelMapper;

    AiProviderService service;
    HttpClient mockHttpClient;

    @BeforeEach
    void setup() {
        service = new AiProviderService(providerMapper, modelMapper, new ObjectMapper());
        mockHttpClient = mock(HttpClient.class);
        ReflectUtil.setFinalField(service, "httpClient", mockHttpClient);
    }

    private SysAiProvider provider(Long id, String name, String ep, String key, Integer enabled, String avail) {
        SysAiProvider p = new SysAiProvider();
        p.setId(id);
        p.setName(name);
        p.setEndpoint(ep);
        p.setApiKey(key);
        p.setEnabled(enabled);
        p.setAvailableModels(avail);
        return p;
    }

    private AiProviderDTO dto(Long id, String name, String ep, String key, Integer enabled) {
        AiProviderDTO d = new AiProviderDTO();
        d.setId(id);
        d.setName(name);
        d.setEndpoint(ep);
        d.setApiKey(key);
        d.setEnabled(enabled);
        return d;
    }

    // ===== 列表脱敏 =====

    @Test
    void list_长key_脱敏() {
        when(providerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(provider(1L, "p", "ep", "sk-abcdefghijklmnop", 1, "")));
        List<AiProviderVO> r = service.list();
        assertTrue(r.get(0).getApiKey().contains("****"));
        assertEquals("sk-a", r.get(0).getApiKey().substring(0, 4));
    }

    @Test
    void list_短key_全脱敏() {
        when(providerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(provider(1L, "p", "ep", "short", 1, "")));
        assertEquals("****", service.list().get(0).getApiKey());
    }

    @Test
    void list_空key_空串() {
        when(providerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(provider(1L, "p", "ep", "", 1, "")));
        assertEquals("", service.list().get(0).getApiKey());
    }

    @Test
    void list_availableModels_解析为列表() {
        when(providerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(provider(1L, "p", "ep", "k", 1, "a,b,c")));
        List<String> avail = service.list().get(0).getAvailableModels();
        assertEquals(List.of("a", "b", "c"), avail);
    }

    // ===== getRawById 校验 =====

    @Test
    void getRawById_不存在_抛() {
        when(providerMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getRawById(1L));
    }

    @Test
    void getRawById_正常() {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "ep", "k", 1, ""));
        SysAiProvider p = service.getRawById(1L);
        assertEquals("p", p.getName());
    }

    // ===== create / update / delete / toggle =====

    @Test
    void create_正常_apiKey空转空串() {
        service.create(dto(null, "p", "ep", null, 1));
        verify(providerMapper).insert(any(SysAiProvider.class));
    }

    @Test
    void update_id空_抛() {
        assertThrows(BusinessException.class, () -> service.update(dto(null, "p", "ep", "k", 1)));
    }

    @Test
    void update_不存在_抛() {
        when(providerMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.update(dto(1L, "p", "ep", "k", 1)));
    }

    @Test
    void update_apiKey空_保留原值() {
        SysAiProvider exist = provider(1L, "p", "ep", "oldsecret", 1, "");
        when(providerMapper.selectById(1L)).thenReturn(exist);
        service.update(dto(1L, "p2", "ep2", null, 1));
        assertEquals("oldsecret", exist.getApiKey());
        verify(providerMapper).updateById(exist);
    }

    @Test
    void update_apiKey有_更新() {
        SysAiProvider exist = provider(1L, "p", "ep", "oldsecret", 1, "");
        when(providerMapper.selectById(1L)).thenReturn(exist);
        service.update(dto(1L, "p2", "ep2", "newkey", 1));
        assertEquals("newkey", exist.getApiKey());
        verify(providerMapper).updateById(exist);
    }

    @Test
    void delete_不存在_抛() {
        when(providerMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    void delete_正常_级联删模型() {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "ep", "k", 1, ""));
        service.delete(1L);
        verify(modelMapper).delete(any(LambdaQueryWrapper.class));
        verify(providerMapper).deleteById(1L);
    }

    @Test
    void toggleEnabled_不存在_抛() {
        when(providerMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.toggleEnabled(1L, 1));
    }

    @Test
    void toggleEnabled_正常() {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "ep", "k", 1, ""));
        service.toggleEnabled(1L, 0);
        verify(providerMapper).updateById(any(SysAiProvider.class));
    }

    // ===== previewModels / testConnection 前置校验（不发真实 HTTP） =====

    @Test
    void previewModels_endpoint空_抛() {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "", "k", 1, ""));
        assertThrows(BusinessException.class, () -> service.previewModels(1L));
    }

    @Test
    void previewModels_apiKey空_抛() {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "ep", "", 1, ""));
        assertThrows(BusinessException.class, () -> service.previewModels(1L));
    }

    @Test
    void testConnection_endpoint空_抛() {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "", "k", 1, ""));
        assertThrows(BusinessException.class, () -> service.testConnection(1L, null));
    }

    @Test
    void testConnection_apiKey空_抛() {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "ep", "", 1, ""));
        assertThrows(BusinessException.class, () -> service.testConnection(1L, null));
    }

    // ===== previewModels HTTP 路径（仅预览不落库） =====

    @Test
    void previewModels_成功_排序_不落库() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"data\":[{\"id\":\"b\"},{\"id\":\"a\"}]}");
        doReturn(resp).when(mockHttpClient).send(any(), any());

        List<String> models = service.previewModels(1L);

        assertEquals(List.of("a", "b"), models);
        // 预览不改库
        verify(modelMapper, never()).insert(any(SysAiModel.class));
        verify(providerMapper, never()).updateById(any(SysAiProvider.class));
    }

    @Test
    void previewModels_空数据_返回空() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{\"data\":[]}");
        doReturn(resp).when(mockHttpClient).send(any(), any());

        List<String> models = service.previewModels(1L);

        assertTrue(models.isEmpty());
    }

    @Test
    void previewModels_非200_抛() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(500);
        doReturn(resp).when(mockHttpClient).send(any(), any());

        BusinessException e = assertThrows(BusinessException.class, () -> service.previewModels(1L));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    void previewModels_网络异常_包装抛() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        doThrow(new java.io.IOException("conn reset")).when(mockHttpClient).send(any(), any());

        BusinessException e = assertThrows(BusinessException.class, () -> service.previewModels(1L));
        assertTrue(e.getMessage().contains("conn reset"));
    }

    // ===== saveSelectedModels 覆盖式保存 =====

    @Test
    void saveSelectedModels_覆盖式_删旧插新并更新availableModels() {
        SysAiProvider p = provider(1L, "p", "ep", "k", 1, "old");
        when(providerMapper.selectById(1L)).thenReturn(p);

        service.saveSelectedModels(1L, List.of(" b ", "a", "b", " c "));

        // 先删旧模型
        verify(modelMapper).delete(any(LambdaQueryWrapper.class));
        // 去重后按顺序插 3 个（b/a/c）
        ArgumentCaptor<SysAiModel> captor = ArgumentCaptor.forClass(SysAiModel.class);
        verify(modelMapper, times(3)).insert(captor.capture());
        List<SysAiModel> inserted = captor.getAllValues();
        assertEquals(List.of("b", "a", "c"), inserted.stream().map(SysAiModel::getModel).toList());
        assertEquals(0, inserted.get(0).getSortOrder());
        // availableModels 更新为逗号串
        ArgumentCaptor<SysAiProvider> pCaptor = ArgumentCaptor.forClass(SysAiProvider.class);
        verify(providerMapper).updateById(pCaptor.capture());
        assertEquals("b,a,c", pCaptor.getValue().getAvailableModels());
    }

    @Test
    void saveSelectedModels_空列表_只删不插() {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "ep", "k", 1, ""));
        service.saveSelectedModels(1L, List.of());
        verify(modelMapper).delete(any(LambdaQueryWrapper.class));
        verify(modelMapper, never()).insert(any(SysAiModel.class));
        ArgumentCaptor<SysAiProvider> pCaptor = ArgumentCaptor.forClass(SysAiProvider.class);
        verify(providerMapper).updateById(pCaptor.capture());
        assertEquals("", pCaptor.getValue().getAvailableModels());
    }

    @Test
    void saveSelectedModels_null_当空处理() {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "ep", "k", 1, ""));
        service.saveSelectedModels(1L, null);
        verify(modelMapper).delete(any(LambdaQueryWrapper.class));
        verify(modelMapper, never()).insert(any(SysAiModel.class));
    }

    // ===== clearModels 清空 =====

    @Test
    void clearModels_清空模型与availableModels() {
        SysAiProvider p = provider(1L, "p", "ep", "k", 1, "a,b");
        when(providerMapper.selectById(1L)).thenReturn(p);
        service.clearModels(1L);
        verify(modelMapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<SysAiProvider> pCaptor = ArgumentCaptor.forClass(SysAiProvider.class);
        verify(providerMapper).updateById(pCaptor.capture());
        assertEquals("", pCaptor.getValue().getAvailableModels());
    }

    @Test
    void clearModels_供应商不存在_抛() {
        when(providerMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.clearModels(1L));
    }

    // ===== testConnection HTTP 路径 =====

    @Test
    void testConnection_成功_指定model() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{}");
        doReturn(resp).when(mockHttpClient).send(any(), any());

        service.testConnection(1L, "gpt-4");

        verify(modelMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void testConnection_成功_model为空_查任一启用模型() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        SysAiModel m = new SysAiModel();
        m.setProviderId(1L);
        m.setModel("gpt-4");
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(m);
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(200);
        when(resp.body()).thenReturn("{}");
        doReturn(resp).when(mockHttpClient).send(any(), any());

        service.testConnection(1L, null);
    }

    @Test
    void testConnection_model为空且无启用模型_抛() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        when(modelMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> service.testConnection(1L, null));
        assertTrue(e.getMessage().contains("模型名称"));
        verify(mockHttpClient, never()).send(any(), any());
    }

    @Test
    void testConnection_非200_带body截断() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(401);
        when(resp.body()).thenReturn("x".repeat(400));
        doReturn(resp).when(mockHttpClient).send(any(), any());

        BusinessException e = assertThrows(BusinessException.class, () -> service.testConnection(1L, "gpt-4"));
        assertTrue(e.getMessage().contains("401"));
        assertTrue(e.getMessage().contains("..."));
    }

    @Test
    void testConnection_非200_空body() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(503);
        when(resp.body()).thenReturn("");
        doReturn(resp).when(mockHttpClient).send(any(), any());

        BusinessException e = assertThrows(BusinessException.class, () -> service.testConnection(1L, "gpt-4"));
        assertEquals("连通失败: HTTP 503", e.getMessage());
    }

    @Test
    void testConnection_网络异常_包装抛() throws Exception {
        when(providerMapper.selectById(1L)).thenReturn(provider(1L, "p", "https://api.x.com", "k", 1, ""));
        doThrow(new java.io.IOException("timeout")).when(mockHttpClient).send(any(), any());

        BusinessException e = assertThrows(BusinessException.class, () -> service.testConnection(1L, "gpt-4"));
        assertTrue(e.getMessage().contains("timeout"));
    }
}
