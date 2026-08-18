package com.stellar.ai.service.rag;

import com.stellar.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * LLM 查询改写（默认实现）：非流式调 TEXT 模型改写，失败回退原查询。
 * <p>loop 模式下改写器接收上一轮 Judger 的缺口描述（gap），生成"补足缺口"的查询；
 * 无 gap 时只做口语化/指代清理。改写是额外的一次 LLM 调用（记 token 到 sys_ai_usage）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmQueryRewriter implements QueryRewriter {

    private final AiChatService aiChatService;

    /** 改写提示词：约束只输出改写结果本身，便于直接解析。 */
    static final String REWRITE_PROMPT =
            "你是检索查询改写器。把用户的问题改写为一个更适合知识库/笔记语义检索的查询："
                    + "去除口语化冗余、补全指代（他/它/那个）、明确主题关键词。"
                    + "只输出改写后的查询本身，不要解释、不要加引号、不要编号。\n\n原始问题：\n";

    /** loop 补查模式：上一轮判定缺的内容注入改写方向（append 到原始问题之后）。 */
    static final String GAP_PROMPT =
            "\n\n注意：上一轮检索发现以下资料缺口，请把你改写出的查询向补足缺口信息的方向扩展"
                    + "（可换检索词/加限定词/拆成子主题，但仍是单个查询，不要多列问题）：\n";

    /** 多轮上下文提示词前缀：把最近几轮对话提供给改写器补全指代。 */
    static final String HISTORY_PROMPT =
            "\n\n以下是对话历史（最近的几条，用于补全指代/理解上下文；不要改写历史，"
                    + "只把最后的问题改写为适合检索的查询）：\n";

    /** 参与上下文的历史轮次上限 */
    private static final int HISTORY_TURNS = 4;
    /** 单条历史内容截断长度（防 prompt 过长） */
    private static final int HISTORY_LEN = 200;

    @Override
    public String rewrite(String query, Long modelId) {
        return rewrite(query, null, modelId);
    }

    @Override
    public String rewrite(String query, String gap, Long modelId) {
        return rewrite(query, gap, modelId, null);
    }

    @Override
    public String rewrite(String query, String gap, Long modelId, List<Map<String, String>> history) {
        if (!StringUtils.hasText(query)) {
            return query;
        }
        long start = System.currentTimeMillis();
        try {
            String prompt = REWRITE_PROMPT + query
                    + historyPrompt(history)
                    + (StringUtils.hasText(gap) ? GAP_PROMPT + gap : "");
            String rewritten = aiChatService.chatCompletionWithMessages(
                    List.of(Map.of("role", "user", "content", prompt)), modelId);
            if (StringUtils.hasText(rewritten)) {
                String cleaned = rewritten.strip().replaceAll("^[\"'“”]+|[\"'“”]+$", "");
                if (!cleaned.isEmpty() && cleaned.length() <= 200) {
                    log.info("[RAG管线] 查询改写{}: \"{}\" -> \"{}\" 耗时={}ms",
                            StringUtils.hasText(gap) ? "(补缺)" : "", truncate(query), cleaned,
                            System.currentTimeMillis() - start);
                    return cleaned;
                }
                log.warn("[RAG管线] 改写结果过长/为空，回退原查询 len={}", rewritten.length());
                return query;
            }
            return query;
        } catch (Exception e) {
            log.warn("[RAG管线] 查询改写失败，回退原查询: {}", e.getMessage());
            return query;
        }
    }

    /** 拼装会话历史块（最多 HISTORY_TURNS 轮，单条截断 HISTORY_LEN 字；空/无则返回空串）。 */
    private String historyPrompt(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(HISTORY_PROMPT);
        int shown = 0;
        for (Map<String, String> turn : history) {
            if (shown >= HISTORY_TURNS) {
                break;
            }
            String role = turn == null ? "user" : String.valueOf(turn.getOrDefault("role", "user"));
            String content = turn == null ? "" : String.valueOf(turn.getOrDefault("content", ""));
            if (content.isBlank()) {
                continue;
            }
            if (content.length() > HISTORY_LEN) {
                content = content.substring(0, HISTORY_LEN) + "…";
            }
            sb.append("assistant".equals(role) ? "助手：" : "用户：").append(content).append('\n');
            shown++;
        }
        return sb.toString();
    }

    private String truncate(String s) {
        return s == null ? "" : (s.length() > 60 ? s.substring(0, 60) + "…" : s);
    }
}