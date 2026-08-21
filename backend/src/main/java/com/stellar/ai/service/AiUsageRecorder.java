package com.stellar.ai.service;

import com.stellar.ai.entity.AiTask;
import com.stellar.ai.vo.AiResolvedConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 调用用量与历史记录：token 消费计费（有 usage 用精确值，否则字符估算兜底）+
 * 文本生成历史落库（ai_task，task_type=text，幂等守卫只记一次）。
 * <p>从 AiChatService 抽出，让聊天服务只管编排，计费/落库是独立关注点。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUsageRecorder {

    private final SysAiUsageService sysAiUsageService;
    private final AiTaskService aiTaskService;

    /**
     * 字符估算 token（仅当 LLM 不返回 usage 时兜底）。
     * <p>中文约 1.5 字符/token，英文与数字约 4 字符/token（与 tiktoken 量级近似）；
     * 原实现直接取字符数，对英文严重高估、污染 sys_ai_usage 统计，故按语种区分。
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjk = 0, latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;                         // CJK 统一表意文字
            } else if (Character.isLetterOrDigit(c)) {
                latin++;
            }
            // 标点/空白不计入 token 估算
        }
        return (int) Math.ceil(cjk / 1.5 + latin / 4.0);
    }

    /**
     * 统一记录 token 消费：有 usage 用精确值，否则按 prompt/result 估算兜底。
     * <p>记录失败仅记日志，不影响主流程。
     */
    public void recordTokenUsage(AiResolvedConfig cfg, String model, String promptEstimateText,
                                 String result, boolean hasUsage, int[] usage,
                                 String subjectType, String subjectId) {
        int promptTokens;
        int completionTokens;
        int totalTokens;
        String source;
        if (hasUsage && usage != null) {
            promptTokens = usage[0];
            completionTokens = usage[1];
            totalTokens = usage[2];
            source = "usage";
        } else {
            promptTokens = estimateTokens(promptEstimateText);
            completionTokens = estimateTokens(result);
            totalTokens = promptTokens + completionTokens;
            source = "estimate";
        }
        try {
            sysAiUsageService.record(subjectType, subjectId, cfg.providerId(), model, cfg.modelType(),
                    promptTokens, completionTokens, totalTokens, source);
        } catch (Exception e) {
            log.warn("记录 token usage 失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /** 多轮场景把 messages 实际 content 拼接估算 prompt token，再委托 {@link #recordTokenUsage}。 */
    public void recordTokenUsageForMessages(AiResolvedConfig cfg, String model, List<?> messages,
                                            String result, boolean hasUsage, int[] usage,
                                            String subjectType, String subjectId) {
        StringBuilder promptText = new StringBuilder();
        for (Object m : messages) {
            if (m instanceof Map<?, ?> map) {
                Object content = map.get("content");
                if (content != null) {
                    promptText.append(content);
                }
            }
        }
        recordTokenUsage(cfg, model, promptText.toString(), result, hasUsage, usage, subjectType, subjectId);
    }

    /**
     * 落库一次文本生成历史（幂等守卫：每条请求只记一次，避免异常路径重复落库）。
     * <p>历史落库异常仅记日志，不影响流式主流程。
     */
    public void recordHistory(boolean[] recorded, String subjectType, String subjectId,
                              Long providerId, String model, String prompt, String result,
                              String status, String errorMsg,
                              LocalDateTime requestTime, long requestTimeMillis) {
        if (recorded[0]) {
            return;
        }
        recorded[0] = true;
        LocalDateTime responseTime = LocalDateTime.now();
        long durationMs = System.currentTimeMillis() - requestTimeMillis;
        AiTask task = new AiTask();
        task.setTaskType("text");
        task.setSubjectType(subjectType);
        task.setSubjectId(subjectId);
        task.setProviderId(providerId);
        task.setModel(model);
        task.setPrompt(prompt);
        task.setResult(result);
        task.setStatus(status);
        task.setErrorMsg(errorMsg);
        task.setRequestTime(requestTime);
        task.setResponseTime(responseTime);
        task.setDurationMs(durationMs);
        aiTaskService.record(task);
    }
}