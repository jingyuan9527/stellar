package com.stellar.ai.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.service.AiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LlmJudger} 单测：JSON 解析（sufficient/gap）、宽松回退、异常保守放行、
 * 无资料注入提前判不足（触发补查）。
 */
class LlmJudgerTest {

    private AiChatService aiChatService;
    private LlmJudger judger;

    @BeforeEach
    void setUp() {
        aiChatService = mock(AiChatService.class);
        judger = new LlmJudger(aiChatService, new ObjectMapper());
    }

    private RagHit hit(String key) {
        return new RagHit("memos", key, "内容" + key, null, null, 0.9);
    }

    @Test
    void judge_输出JSON足够_放行() {
        when(aiChatService.chatCompletionWithMessages(any(), any()))
                .thenReturn("{\"sufficient\": true, \"gap\": \"\"}");
        assertTrue(judger.judge("q", List.of(hit("1")), null).sufficient());
    }

    @Test
    void judge_输出JSON不足_带缺口() {
        when(aiChatService.chatCompletionWithMessages(any(), any()))
                .thenReturn("{\"sufficient\": false, \"gap\": \"缺少项目的技术栈信息\"}");
        Judgement j = judger.judge("q", List.of(hit("1")), null);
        assertFalse(j.sufficient());
        assertEquals("缺少项目的技术栈信息", j.gap());
    }

    @Test
    void judge_夹带解释文本_宽松提取JSON() {
        when(aiChatService.chatCompletionWithMessages(any(), any()))
                .thenReturn("以下是判断结果：{\"sufficient\":false,\"gap\":\"缺部署说明\"} 希望有帮助");
        Judgement j = judger.judge("q", List.of(hit("1")), null);
        assertFalse(j.sufficient());
        assertEquals("缺部署说明", j.gap());
    }

    @Test
    void judge_无JSON_宽松回退false关键字() {
        when(aiChatService.chatCompletionWithMessages(any(), any()))
                .thenReturn("sufficient:false 资料不足");
        assertFalse(judger.judge("q", List.of(hit("1")), null).sufficient());
    }

    @Test
    void judge_宽松回退_带空格变体() {
        // 修复点（P3）：去空白后匹配，"sufficient: false"（带空格）也应判不足
        when(aiChatService.chatCompletionWithMessages(any(), any()))
                .thenReturn("sufficient: false 资料不足");
        assertFalse(judger.judge("q", List.of(hit("1")), null).sufficient());
    }

    @Test
    void judge_无JSON_宽松回退默认放行() {
        when(aiChatService.chatCompletionWithMessages(any(), any()))
                .thenReturn("我不确定，随便吧");
        assertTrue(judger.judge("q", List.of(hit("1")), null).sufficient());
    }

    @Test
    void judge_LLM异常_保守放行() {
        when(aiChatService.chatCompletionWithMessages(any(), any()))
                .thenThrow(new RuntimeException("boom"));
        assertTrue(judger.judge("q", List.of(hit("1")), null).sufficient());
    }

    @Test
    void judge_无资料命中_直接判不足触发补查() {
        // 不调 LLM：空资料一定不足，给出通用缺口
        Judgement j = judger.judge("q", List.of(), null);
        assertFalse(j.sufficient());
        assertTrue(j.gap() != null && !j.gap().isBlank());
    }
}