package com.stellar.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.vo.ToolResult;
import com.stellar.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link AiAgentLoopService} 单测：Mock 掉 LLM 调用，覆盖
 * 直接返回最终回答、工具轮回填消息、轮数上限、tools 不支持降级（仅限零工具轮）。
 */
@ExtendWith(MockitoExtension.class)
class AiAgentLoopServiceTest {

    @Mock
    private AiChatService aiChatService;

    private AiAgentLoopService service;

    /** 记录收到的 toolCall，返回固定结果 */
    private final AiAgentLoopService.AgentToolExecutor executor = toolCall ->
            new ToolResult(toolCall.path("id").asText(""), "工具结果:" + toolCall.path("function").path("name").asText(),
                    null, null);

    @BeforeEach
    void setUp() {
        service = new AiAgentLoopService(aiChatService, new ObjectMapper());
    }

    private List<Map<String, String>> messages() {
        return List.of(Map.of("role", "user", "content", "任务"));
    }

    private List<Map<String, Object>> tools() {
        return List.of(Map.of("type", "function"));
    }

    /** 构造带 tool_calls 的完整响应 JsonNode */
    private JsonNode toolCallResponse(String callId) {
        String json = """
                {"choices":[{"message":{"content":null,"tool_calls":[
                  {"id":"%s","type":"function","function":{"name":"fetch_url","arguments":"{\\"url\\":\\"https://example.com\\"}"}}
                ]}}]}""".formatted(callId);
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 构造纯文本最终回答响应 */
    private JsonNode contentResponse(String content) {
        String json = "{\"choices\":[{\"message\":{\"content\":\"%s\"}}]}".formatted(content);
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 无toolCalls_直接返回内容() {
        when(aiChatService.chatCompletionWithTools(anyList(), any(), eq(null)))
                .thenReturn(contentResponse("标签a、标签b"));

        String result = service.run(messages(), tools(), executor, null);

        assertEquals("标签a、标签b", result);
        verify(aiChatService, times(1)).chatCompletionWithTools(anyList(), any(), eq(null));
    }

    @Test
    void 一轮工具调用_回填assistant与tool消息后返回最终回答() {
        when(aiChatService.chatCompletionWithTools(anyList(), any(), eq(null)))
                .thenReturn(toolCallResponse("call-1"))
                .thenReturn(contentResponse("最终标签"));

        String result = service.run(messages(), tools(), executor, null);

        assertEquals("最终标签", result);
        ArgumentCaptor<List<Map<String, Object>>> captor = forListCaptor();
        verify(aiChatService, times(2)).chatCompletionWithTools(captor.capture(), any(), eq(null));
        List<Map<String, Object>> secondMessages = captor.getAllValues().get(1);
        assertEquals(3, secondMessages.size());
        Map<String, Object> assistantMsg = secondMessages.get(1);
        assertEquals("assistant", assistantMsg.get("role"));
        assertNotNull(assistantMsg.get("tool_calls"));
        Map<String, Object> toolMsg = secondMessages.get(2);
        assertEquals("tool", toolMsg.get("role"));
        assertEquals("call-1", toolMsg.get("tool_call_id"));
        assertEquals("工具结果:fetch_url", toolMsg.get("content"));
    }

    @Test
    void 轮数上限仍未给出答案_抛异常() {
        when(aiChatService.chatCompletionWithTools(anyList(), any(), eq(null)))
                .thenAnswer(inv -> toolCallResponse("call-" + System.nanoTime()));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.run(messages(), tools(), executor, null));

        assertTrue(e.getMessage().contains("上限"));
        verify(aiChatService, times(AiAgentLoopService.MAX_TOOL_ROUNDS))
                .chatCompletionWithTools(anyList(), any(), eq(null));
    }

    @Test
    void 首次请求HTTP400_降级纯文本调用() {
        when(aiChatService.chatCompletionWithTools(anyList(), any(), eq(null)))
                .thenThrow(new BusinessException("LLM 返回错误: HTTP 400"));
        when(aiChatService.chatCompletionWithMessages(any(), eq(null))).thenReturn("降级标签");

        String result = service.runWithDegrade(messages(), tools(), executor, null);

        assertEquals("降级标签", result);
        verify(aiChatService, times(1)).chatCompletionWithMessages(messages(), null);
    }

    @Test
    void 已执行过工具轮后的失败_不降级直接抛() {
        when(aiChatService.chatCompletionWithTools(anyList(), any(), eq(null)))
                .thenReturn(toolCallResponse("call-1"))
                .thenThrow(new BusinessException("LLM 返回错误: HTTP 400"));

        assertThrows(BusinessException.class,
                () -> service.runWithDegrade(messages(), tools(), executor, null));

        verify(aiChatService, never()).chatCompletionWithMessages(any(), any());
    }

    @Test
    void 非HTTP400失败_不降级() {
        when(aiChatService.chatCompletionWithTools(anyList(), any(), eq(null)))
                .thenThrow(new BusinessException("LLM 返回错误: HTTP 500"));

        assertThrows(BusinessException.class,
                () -> service.runWithDegrade(messages(), tools(), executor, null));

        verify(aiChatService, never()).chatCompletionWithMessages(any(), any());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<Map<String, Object>>> forListCaptor() {
        return ArgumentCaptor.forClass((Class) List.class);
    }
}
