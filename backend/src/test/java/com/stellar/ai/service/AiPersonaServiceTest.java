package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.ai.AiBuiltinPersonas;
import com.stellar.ai.dto.AiPersonaDTO;
import com.stellar.ai.entity.AiPersona;
import com.stellar.ai.mapper.AiPersonaMapper;
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
 * {@link AiPersonaService} 单测：单依赖 personaMapper，覆盖启用/全量列表查询、
 * 增改删 CRUD 与各类归属/存在性校验，以及内置人设恢复默认（成功 + 非内置抛错）。
 */
@ExtendWith(MockitoExtension.class)
class AiPersonaServiceTest {

    @Mock
    AiPersonaMapper personaMapper;

    AiPersonaService service;

    @BeforeEach
    void setup() {
        service = new AiPersonaService(personaMapper);
    }

    private AiPersonaDTO dto(String name) {
        AiPersonaDTO d = new AiPersonaDTO();
        d.setName(name);
        d.setSystemPrompt("sys");
        d.setDescription("desc");
        d.setEnabled(1);
        d.setSortOrder(0);
        return d;
    }

    private AiPersona persona(Long id, Integer builtIn) {
        AiPersona p = new AiPersona();
        p.setId(id);
        p.setName("通用助手");
        p.setSystemPrompt("sys");
        p.setBuiltIn(builtIn);
        return p;
    }

    @Test
    void listEnabled_正常() {
        when(personaMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new AiPersona()));
        assertEquals(1, service.listEnabled().size());
    }

    @Test
    void listAll_正常() {
        when(personaMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new AiPersona()));
        assertEquals(1, service.listAll().size());
    }

    @Test
    void create_正常_强制内置为0并trim() {
        service.create(dto(" 名字 "));
        ArgumentCaptor<AiPersona> cap = ArgumentCaptor.forClass(AiPersona.class);
        verify(personaMapper).insert(cap.capture());
        AiPersona p = cap.getValue();
        assertEquals("名字", p.getName());
        assertEquals(0, p.getBuiltIn());
    }

    @Test
    void update_id为空_抛() {
        AiPersonaDTO d = dto("x");
        d.setId(null);
        assertThrows(BusinessException.class, () -> service.update(d));
    }

    @Test
    void update_不存在_抛() {
        AiPersonaDTO d = dto("x");
        d.setId(1L);
        when(personaMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.update(d));
    }

    @Test
    void update_正常_更新字段() {
        AiPersonaDTO d = dto("新名");
        d.setId(1L);
        when(personaMapper.selectById(1L)).thenReturn(persona(1L, 0));
        service.update(d);
        ArgumentCaptor<AiPersona> cap = ArgumentCaptor.forClass(AiPersona.class);
        verify(personaMapper).updateById(cap.capture());
        assertEquals("新名", cap.getValue().getName());
    }

    @Test
    void toggleEnabled_不存在_抛() {
        when(personaMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.toggleEnabled(1L, 1));
    }

    @Test
    void toggleEnabled_正常() {
        when(personaMapper.selectById(1L)).thenReturn(persona(1L, 0));
        service.toggleEnabled(1L, 0);
        ArgumentCaptor<AiPersona> cap = ArgumentCaptor.forClass(AiPersona.class);
        verify(personaMapper).updateById(cap.capture());
        assertEquals(0, cap.getValue().getEnabled());
    }

    @Test
    void delete_不存在_抛() {
        when(personaMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    void delete_内置不可删_抛() {
        when(personaMapper.selectById(1L)).thenReturn(persona(1L, 1));
        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    void delete_正常() {
        when(personaMapper.selectById(1L)).thenReturn(persona(1L, 0));
        service.delete(1L);
        verify(personaMapper).deleteById(1L);
    }

    @Test
    void resetBuiltin_不存在_抛() {
        when(personaMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.resetBuiltin(1L));
    }

    @Test
    void resetBuiltin_非内置_抛() {
        when(personaMapper.selectById(1L)).thenReturn(persona(1L, 0));
        assertThrows(BusinessException.class, () -> service.resetBuiltin(1L));
    }

    @Test
    void resetBuiltin_成功_恢复种子配置() {
        AiPersona seedMatch = persona(1L, 1);
        seedMatch.setName("程序员");
        when(personaMapper.selectById(1L)).thenReturn(seedMatch);
        service.resetBuiltin(1L);
        ArgumentCaptor<AiPersona> cap = ArgumentCaptor.forClass(AiPersona.class);
        verify(personaMapper).updateById(cap.capture());
        AiBuiltinPersonas.Seed seed = AiBuiltinPersonas.findByName("程序员");
        assertEquals(seed.systemPrompt(), cap.getValue().getSystemPrompt());
        assertEquals(seed.description(), cap.getValue().getDescription());
    }
}
