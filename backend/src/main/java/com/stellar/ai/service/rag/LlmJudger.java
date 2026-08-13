package com.stellar.ai.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 充分性判定器（默认实现）：把问题 + 当前 top 命中交给 TEXT 模型判"够不够"。
 * <p>解析失败/调用异常一律返回 {@link Judgement#sufficient()} 保守放行，防死循环。
 * gap 截断 120 字避免下一轮改写 prompt 过长。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmJudger implements Judger {

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    /** 单条命中注入判定 prompt 的文本上限 */
    private static final int SNIPPET_LEN = 300;
    /** gap 上限，防止缺口描述失控撑爆下一轮改写 prompt */
    private static final int GAP_MAX = 120;

    /** 宽松抽取 JSON：兼容 LLM 输出夹带解释/代码块的情况 */
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{.*}");

    static final String JUDGE_PROMPT_PREFIX =
            "你是检索充分性判定器。用户问题如下，你将看到已检索到的资料片段（可能与问题无关）。"
                    + "判断：仅凭这些资料能否完整回答用户问题？"
                    + "输出 JSON（不要解释）：{\"sufficient\": true或false, \"gap\": \"资料缺少什么/回答不出来什么（sufficient为true时为\"\"）\"}\n\n"
                    + "用户问题：\n%s\n\n检索到的资料：\n";

    @Override
    public Judgement judge(String query, List<RagHit> hits, Long modelId) {
        if (!StringUtils.hasText(query)) {
            return Judgement.ok();
        }
        // 无资料注入：直接判不足（有缺口），触发补查——比空资料直接生成更符合意图
        if (hits == null || hits.isEmpty()) {
            return new Judgement(false, "当前检索没有找到任何相关资料");
        }
        StringBuilder sb = new StringBuilder(String.format(JUDGE_PROMPT_PREFIX, query));
        int idx = 1;
        for (RagHit h : hits) {
            String text = h.text() == null ? "" : h.text().replace('\n', ' ');
            if (text.length() > SNIPPET_LEN) {
                text = text.substring(0, SNIPPET_LEN) + "…";
            }
            sb.append(idx++).append(": ").append(text).append('\n');
        }

        long start = System.currentTimeMillis();
        try {
            String result = aiChatService.chatCompletionWithMessages(
                    List.of(Map.of("role", "user", "content", sb.toString())), modelId);
            Judgement j = parse(result);
            log.info("[RAG管线] 充分性判定 hits={} sufficient={} gap={} 耗时={}ms",
                    hits.size(), j.sufficient(), truncate(j.gap()), System.currentTimeMillis() - start);
            return j;
        } catch (Exception e) {
            // 调用失败保守放行（视为足够），避免 loop 因判定器故障卡死
            log.warn("[RAG管线] 充分性判定失败，保守放行: {}", e.getMessage());
            return Judgement.ok();
        }
    }

    /**
     * 解析 LLM 输出 JSON：优先 JSON 提取，失败回退正则找 sufficient 字样。
     * 一切失败返回"足够"（保守）。
     */
    Judgement parse(String text) {
        if (!StringUtils.hasText(text)) {
            return Judgement.ok();
        }
        try {
            Matcher m = JSON_BLOCK.matcher(text);
            if (m.find()) {
                JsonNode node = objectMapper.readTree(m.group(0));
                boolean sufficient = node.path("sufficient").asBoolean(true);
                if (!sufficient) {
                    String gap = node.path("gap").asText("");
                    return new Judgement(false, gap.length() > GAP_MAX ? gap.substring(0, GAP_MAX) : gap);
                }
                return Judgement.ok();
            }
        } catch (Exception ignored) {
            // 非严格 JSON，走宽松回退
        }
        // 宽松回退：输出里出现 false → 判不够；否则足够
        String cleaned = text.trim().toLowerCase();
        if (cleaned.contains("\"sufficient\":false") || cleaned.contains("sufficient=false")
                || cleaned.contains("sufficient:false")) {
            return new Judgement(false, null);
        }
        return Judgement.ok();
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}