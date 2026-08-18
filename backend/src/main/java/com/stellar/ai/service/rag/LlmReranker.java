package com.stellar.ai.service.rag;

import com.stellar.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM 重排（默认实现）：把候选（截断文本 + 编号）交给 TEXT 模型选 topK，
 * 解析返回编号顺序精排。失败/解析失败时按原顺序截断返回（降级不中断）。
 * <p>候选上限 {@link #maxCandidates} 控制单次重排的 token 开销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmReranker implements Reranker {

    private final AiChatService aiChatService;

    /** 单次重排候选上限（超出截断，防 prompt 过长；默认 12，应与 stellar.rag.pool-size 联动） */
    @Value("${stellar.rag.rerank-max-candidates:12}")
    private int maxCandidates = 12;
    /** 单条候选注入的文本上限 */
    private static final int SNIPPET_LEN = 200;

    static final String RERANK_PROMPT_PREFIX =
            "你是检索结果重排器。参考用户问题，从候选资料中选出最相关的前%d条，"
                    + "输出编号列表（如 [3,1,4]，第一个为最相关；只能输出列表，不要解释）。"
                    + "宁可少选，不要选不相关的。\n\n用户问题：\n%s\n\n候选资料：\n";

    @Override
    public List<RagHit> rerank(String query, List<RagHit> candidates, int topK, Long modelId) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return candidates == null ? List.of() : candidates;
        }
        List<RagHit> capped = candidates.size() > maxCandidates
                ? candidates.subList(0, maxCandidates) : new ArrayList<>(candidates);

        StringBuilder sb = new StringBuilder(String.format(RERANK_PROMPT_PREFIX, topK, query));
        for (int i = 0; i < capped.size(); i++) {
            RagHit h = capped.get(i);
            String text = h.text() == null ? "" : h.text().replace('\n', ' ');
            if (text.length() > SNIPPET_LEN) {
                text = text.substring(0, SNIPPET_LEN) + "…";
            }
            sb.append(i + 1).append(": ").append(text).append('\n');
        }

        long start = System.currentTimeMillis();
        try {
            String result = aiChatService.chatCompletionWithMessages(
                    List.of(Map.of("role", "user", "content", sb.toString())), modelId);
            List<Integer> order = parseIndices(result, capped.size());
            if (order.isEmpty()) {
                log.debug("[RAG管线] 重排无有效输出，保持原序 candidates={}", capped.size());
                return capped.size() > topK ? capped.subList(0, topK) : capped;
            }
            // 重排结果 = 选中编号按序 + 未选中的剩余按原序补足
            Set<Integer> picked = new LinkedHashSet<>(order);
            List<RagHit> reranked = new ArrayList<>(topK);
            for (Integer idx : order) {
                if (reranked.size() >= topK) {
                    break;
                }
                if (idx > 0 && idx <= capped.size()) {
                    reranked.add(capped.get(idx - 1));
                }
            }
            if (reranked.size() < topK) {
                for (int i = 0; i < capped.size() && reranked.size() < topK; i++) {
                    if (!picked.contains(i + 1)) {
                        reranked.add(capped.get(i));
                    }
                }
            }
            log.info("[RAG管线] LLM 重排完成 candidates={} picked={} 耗时={}ms",
                    capped.size(), reranked.size(), System.currentTimeMillis() - start);
            return reranked;
        } catch (Exception e) {
            log.warn("[RAG管线] 重排失败，保持原序: {}", e.getMessage());
            return capped.size() > topK ? capped.subList(0, topK) : capped;
        }
    }

    /** 解析 LLM 输出的编号列表（[1,3,2] / ["1","3"] / 1,3,2 均兼容），非法/空返回空列表。 */
    List<Integer> parseIndices(String text, int maxIndex) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        List<Integer> result = new ArrayList<>();
        for (String part : cleaned.split("[^0-9]+")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                int v = Integer.parseInt(part);
                if (v >= 1 && v <= maxIndex && !result.contains(v)) {
                    result.add(v);
                }
            } catch (NumberFormatException ignore) {
                // 跳过非数字
            }
        }
        return result;
    }
}