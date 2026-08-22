package com.stellar.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.vo.ToolResult;
import com.stellar.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用非流式 agent 循环：带 tools 请求 LLM → 执行本轮全部 tool_calls 并回传 →
 * 重复，直到 LLM 给出不含 tool_calls 的最终回答或达到轮数上限。
 * <p>工具定义与执行由调用方注入（{@link AgentToolExecutor}），本类只做循环编排；
 * 首个用例为备忘同步 AI 打标签（fetch_url 工具）。
 * <p>降级：模型/供应商不支持 tools 参数（上游 HTTP 400）且尚未执行过任何工具轮时，
 * 自动退回纯文本调用（{@code chatCompletionWithMessages}），保证任务不因能力缺失而失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentLoopService {

    /** 工具调用轮数上限：防模型反复调工具不出结果导致请求无限耗时 */
    static final int MAX_TOOL_ROUNDS = 5;

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    /**
     * 工具执行器 SPI：调用方把具体工具的分发逻辑注入循环。
     */
    @FunctionalInterface
    public interface AgentToolExecutor {
        ToolResult execute(JsonNode toolCall);
    }

    /**
     * 带 tools 不支持自动降级的入口。推荐外部调用方使用。
     *
     * @param initialMessages 初始消息（system/user，OpenAI 格式）
     * @param tools           OpenAI 兼容 tools 定义
     * @param executor        工具执行器
     * @param modelId         AI 模型 id（TEXT 类型），空则用后端 TEXT 默认模型
     */
    public String runWithDegrade(List<Map<String, String>> initialMessages,
                                 List<Map<String, Object>> tools,
                                 AgentToolExecutor executor, Long modelId) {
        int[] toolRoundsUsed = {0};
        try {
            return runLoop(initialMessages, tools, executor, modelId, toolRoundsUsed);
        } catch (BusinessException e) {
            // 仅在首个请求就因不支持 tools 失败时降级；已执行过工具轮的失败重跑纯文本无意义
            if (toolRoundsUsed[0] == 0 && isToolsUnsupported(e)) {
                log.warn("[AI循环] 上游疑似不支持 tools（HTTP 400），降级纯文本调用 modelId={}", modelId);
                return aiChatService.chatCompletionWithMessages(initialMessages, modelId);
            }
            throw e;
        }
    }

    /**
     * 执行 agent 循环，达到轮数上限仍无最终回答则抛 {@link BusinessException}
     * （由调用方决定该条任务的失败处理）。
     */
    public String run(List<Map<String, String>> initialMessages,
                      List<Map<String, Object>> tools,
                      AgentToolExecutor executor, Long modelId) {
        return runLoop(initialMessages, tools, executor, modelId, new int[]{0});
    }

    private String runLoop(List<Map<String, String>> initialMessages,
                           List<Map<String, Object>> tools,
                           AgentToolExecutor executor, Long modelId,
                           int[] toolRoundsUsed) {
        // 后续轮次需放 tool_calls 数组，统一升级为 Object 值类型（初始消息原样复制）
        List<Map<String, Object>> messages = initialMessages.stream()
                .map(m -> Map.<String, Object>copyOf(m))
                .collect(Collectors.toCollection(ArrayList::new));
        for (int round = 1; round <= MAX_TOOL_ROUNDS; round++) {
            JsonNode resp = aiChatService.chatCompletionWithTools(messages, tools, modelId);
            JsonNode msgNode = resp.path("choices").path(0).path("message");
            JsonNode toolCalls = msgNode.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                log.info("[AI循环] 第{}轮得到最终回答 len={}", round, msgNode.path("content").asText("").length());
                return msgNode.path("content").asText("");
            }
            toolRoundsUsed[0] = round;
            log.info("[AI循环] 第{}轮 LLM 请求调用工具 count={}", round, toolCalls.size());

            messages.add(buildAssistantToolCallMessage(msgNode, toolCalls));
            for (JsonNode toolCall : toolCalls) {
                ToolResult result = executor.execute(toolCall);
                String name = toolCall.path("function").path("name").asText("");
                log.info("[AI循环] 工具执行完成 round={} name={} contentLen={}",
                        round, name, result.content() == null ? 0 : result.content().length());
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", result.toolCallId());
                toolMsg.put("content", result.content() == null ? "" : result.content());
                messages.add(toolMsg);
            }
        }
        throw new BusinessException("工具调用轮数达到上限 " + MAX_TOOL_ROUNDS + "，LLM 未给出最终结果");
    }

    /**
     * 构造 assistant(tool_calls) 消息原样回填——OpenAI 协议要求 tool 消息前必须
     * 有携带对应 tool_calls 的 assistant 消息，否则部分供应商直接拒绝请求。
     */
    private Map<String, Object> buildAssistantToolCallMessage(JsonNode msgNode, JsonNode toolCalls) {
        Map<String, Object> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", msgNode.path("content").isMissingNode()
                || msgNode.path("content").isNull() ? null : msgNode.path("content").asText());
        assistantMsg.put("tool_calls", objectMapper.convertValue(toolCalls, List.class));
        return assistantMsg;
    }

    /** 上游对 tools 参数报 HTTP 400 视为不支持（AiChatService 已把非 200 统一包装为该消息格式） */
    private boolean isToolsUnsupported(BusinessException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("HTTP 400");
    }
}
