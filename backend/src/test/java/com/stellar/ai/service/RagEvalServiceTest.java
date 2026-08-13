package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.entity.AiChatMessage;
import com.stellar.ai.entity.RagEvalCase;
import com.stellar.ai.entity.RagEvalResult;
import com.stellar.ai.entity.RagFeedback;
import com.stellar.ai.mapper.AiChatMessageMapper;
import com.stellar.ai.mapper.RagEvalCaseMapper;
import com.stellar.ai.mapper.RagEvalResultMapper;
import com.stellar.ai.mapper.RagFeedbackMapper;
import com.stellar.ai.service.rag.RagHit;
import com.stellar.ai.service.rag.RagSearchService;
import com.stellar.ai.service.rag.RetrievalResult;
import com.stellar.ai.vo.RagEvalRunVO;
import com.stellar.ai.vo.RagFeedbackVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RagEvalService} 单测：跑分（纯检索路径 recall@k 计算、pass 判定、落 rag_eval_result、
 * runId 生成）、反馈分页带消息快照。纯 Mockito + ReflectionTestUtils 注入 topK。
 */
class RagEvalServiceTest {

    private RagEvalCaseMapper caseMapper;
    private RagEvalResultMapper resultMapper;
    private RagFeedbackMapper feedbackMapper;
    private AiChatMessageMapper messageMapper;
    private RagSearchService ragSearchService;
    private RagEvalService service;

    @BeforeEach
    void setUp() {
        caseMapper = mock(RagEvalCaseMapper.class);
        resultMapper = mock(RagEvalResultMapper.class);
        feedbackMapper = mock(RagFeedbackMapper.class);
        messageMapper = mock(AiChatMessageMapper.class);
        ragSearchService = mock(RagSearchService.class);
        service = new RagEvalService(caseMapper, resultMapper, feedbackMapper,
                messageMapper, ragSearchService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "topK", 4);
    }

    private RagEvalCase caseRow(Long id, String query, String expectedSrcs) {
        RagEvalCase c = new RagEvalCase();
        c.setId(id);
        c.setQuery(query);
        c.setExpectedSources(expectedSrcs);
        return c;
    }

    private RetrievalResult resultWith(String... sourceKeys) {
        List<RagHit> hits = new ArrayList<>();
        for (String k : sourceKeys) {
            String[] parts = k.split(":", 2);
            hits.add(new RagHit(parts[0], parts[1], "t", null, null, 0.9));
        }
        return new RetrievalResult("q", hits, Map.of(), 1);
    }

    @Test
    void runEvaluation_命中期望_recall和pass正确() {
        when(caseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(caseRow(1L, "问题A", "[\"memos:12\",\"kb:3\"]"),
                        caseRow(2L, "问题B", "[\"memos:99\"]")));
        // case1: top 命中 memos:12（期望 2 命中 1 → recall 0.5, pass）
        when(ragSearchService.searchTopK(eq("问题A"), eq(null), eq(true), eq(4)))
                .thenReturn(resultWith("memos:12", "kb:9"));
        // case2: 期望 memos:99 未命中 → recall 0, fail
        when(ragSearchService.searchTopK(eq("问题B"), eq(null), eq(true), eq(4)))
                .thenReturn(resultWith("memos:1", "memos:2"));

        RagEvalRunVO vo = service.runEvaluation();

        assertNotNull(vo.runId());
        assertEquals(2, vo.total());
        assertEquals(1, vo.passCount());
        assertEquals(1, vo.failCount());
        assertEquals(0.25, vo.recallAvg(), 1e-6); // (0.5+0)/2
        // 两条结果都落库
        verify(resultMapper, org.mockito.Mockito.times(2)).insert(any(RagEvalResult.class));
        assertEquals(2, vo.details().size());
        assertEquals(0.5, vo.details().get(0).recall(), 1e-6);
        assertTrue(vo.details().get(0).pass());
        assertFalse(vo.details().get(1).pass());
    }

    @Test
    void runEvaluation_空评估集_抛异常() {
        when(caseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        try {
            service.runEvaluation();
            org.junit.jupiter.api.Assertions.fail("应抛异常");
        } catch (com.stellar.common.BusinessException e) {
            assertTrue(e.getMessage().contains("评估集为空"));
        }
    }

    @Test
    void pageFeedback_联查消息快照() {
        RagFeedback fb = new RagFeedback();
        fb.setId(1L);
        fb.setMessageId(10L);
        fb.setValue(-1);
        fb.setSubjectType("account");
        fb.setSubjectId("1");
        fb.setCreateTime(LocalDateTime.now());
        Page<RagFeedback> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(fb));
        when(feedbackMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        AiChatMessage m = new AiChatMessage();
        m.setId(10L);
        m.setContent("回答内容");
        when(messageMapper.selectBatchIds(anyList())).thenReturn(List.of(m));

        Page<RagFeedbackVO> voPage = service.pageFeedback(-1, 1, 10);

        assertEquals(1, voPage.getRecords().size());
        RagFeedbackVO vo = voPage.getRecords().get(0);
        assertEquals(-1, vo.value());
        assertEquals("回答内容", vo.content());
    }
}