package com.stellar.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.dto.RagEvalCaseDTO;
import com.stellar.ai.entity.RagEvalCase;
import com.stellar.ai.entity.RagEvalResult;
import com.stellar.ai.service.RagEvalService;
import com.stellar.ai.vo.RagEvalRunVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiRagEvalController} 单测：run 的 mode 参数透传（retrieval 默认/full）、
 * 评估集 CRUD、跑分批次回看、反馈分页透传。
 */
@ExtendWith(MockitoExtension.class)
class AiRagEvalControllerTest {

    @Mock
    RagEvalService ragEvalService;

    AiRagEvalController controller;

    @BeforeEach
    void setup() {
        controller = new AiRagEvalController(ragEvalService);
    }

    @Test
    void run_默认纯检索模式() {
        controller.run("retrieval");
        verify(ragEvalService).runEvaluation("retrieval");
    }

    @Test
    void run_full模式透传() {
        controller.run("full");
        verify(ragEvalService).runEvaluation("full");
    }

    @Test
    void pageCases_正常() {
        when(ragEvalService.pageCases(1, 10)).thenReturn(new Page<RagEvalCase>());
        assertNotNull(controller.pageCases(1, 10).getData());
    }

    @Test
    void createCase_正常() {
        RagEvalCaseDTO dto = new RagEvalCaseDTO();
        controller.createCase(dto);
        verify(ragEvalService).createCase(dto);
    }

    @Test
    void updateCase_正常() {
        RagEvalCaseDTO dto = new RagEvalCaseDTO();
        controller.updateCase(dto);
        verify(ragEvalService).updateCase(dto);
    }

    @Test
    void deleteCase_正常() {
        controller.deleteCase(1L);
        verify(ragEvalService).deleteCase(1L);
    }

    @Test
    void runResults_正常() {
        when(ragEvalService.getRunResults("R1")).thenReturn(List.of(new RagEvalResult()));
        assertEquals(1, controller.runResults("R1").getData().size());
    }

    @Test
    void recentRuns_正常() {
        when(ragEvalService.listRecentRuns(20)).thenReturn(List.of("R1"));
        assertEquals(1, controller.recentRuns(20).getData().size());
    }

    @Test
    void feedback_正常() {
        when(ragEvalService.pageFeedback(-1, 1, 10)).thenReturn(new Page<>());
        assertNotNull(controller.feedback(-1, 1, 10).getData());
    }
}
