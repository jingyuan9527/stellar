package com.stellar.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.dto.AiTemplateDTO;
import com.stellar.ai.dto.AiTemplateQueryDTO;
import com.stellar.ai.entity.SysAiTemplate;
import com.stellar.ai.service.AiTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * {@link AiTemplateController} 单测：公开分页、create 用登录用户 ID、update/delete/reset 透传。
 */
@ExtendWith(MockitoExtension.class)
class AiTemplateControllerTest {

    @Mock
    AiTemplateService aiTemplateService;

    AiTemplateController controller;

    @BeforeEach
    void setup() {
        controller = new AiTemplateController(aiTemplateService);
    }

    @Test
    void page_正常() {
        AiTemplateQueryDTO q = new AiTemplateQueryDTO();
        when(aiTemplateService.page(q)).thenReturn(new Page<SysAiTemplate>());
        assertNotNull(controller.page(q).getData());
    }

    @Test
    void create_用登录用户ID() {
        AiTemplateDTO dto = new AiTemplateDTO();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            controller.create(dto);
        }
        verify(aiTemplateService).create(dto, 7L);
    }

    @Test
    void update_正常() {
        AiTemplateDTO dto = new AiTemplateDTO();
        controller.update(1L, dto);
        verify(aiTemplateService).update(1L, dto);
    }

    @Test
    void delete_正常() {
        controller.delete(1L);
        verify(aiTemplateService).delete(1L);
    }

    @Test
    void resetBuiltin_正常() {
        controller.resetBuiltin(1L);
        verify(aiTemplateService).resetBuiltin(1L);
    }
}
