package com.stellar.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.vo.ToolResult;
import com.stellar.infra.SafeUrlValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * fetch_url 工具：抓取网页并提取正文文本回传给 LLM。
 * <p>供 AI agent 循环（如备忘同步打标签）使用：笔记含链接时 LLM 自主调用本工具
 * 获取网页内容，再结合笔记内容完成任务。独立于聊天模块的 {@code AiChatToolService}
 * （画图/TTS 面向游客聊天，本工具面向服务端任务）。
 * <p>安全：走 {@link SafeUrlValidator} 防 SSRF（仅公网 http/https、禁本机/私网地址）；
 * 抓取失败不抛异常——错误信息作为 tool 结果回传，由 LLM 决定后续动作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPageFetchTool {

    private final ObjectMapper objectMapper;

    static final String TOOL_NAME = "fetch_url";

    /** 单页抓取超时（毫秒） */
    private static final int TIMEOUT_MS = 10_000;
    /** 响应体大小上限 2MB，防超大页面拖垮内存 */
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    /** 回传给 LLM 的正文文本上限（字符），防 token 爆炸 */
    private static final int MAX_TEXT_CHARS = 4000;

    /**
     * OpenAI 兼容 tools 定义。
     */
    public Map<String, Object> definition() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", TOOL_NAME,
                        "description", "抓取网页内容。当待处理的内容中包含网页链接（URL），"
                                + "且需要了解链接指向的具体内容时调用此工具获取网页正文。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "url", Map.of(
                                                "type", "string",
                                                "description", "要抓取的完整网页 URL（http/https）"
                                        )
                                ),
                                "required", List.of("url")
                        )
                )
        );
    }

    /**
     * 执行 LLM 返回的 tool_call。任何失败都转为错误文本结果回传，不向上抛。
     */
    public ToolResult execute(JsonNode toolCall) {
        String toolCallId = toolCall.path("id").asText("");
        // OpenAI 协议中 arguments 是 JSON 字符串（非对象节点），需先反序列化
        String argsStr = toolCall.path("function").path("arguments").asText("{}");
        final JsonNode args;
        try {
            args = objectMapper.readTree(argsStr);
        } catch (Exception e) {
            log.warn("[AI工具] fetch_url 参数解析失败 args={}", argsStr);
            return new ToolResult(toolCallId, "抓取失败：参数不是合法 JSON", null, null);
        }
        String url = args.path("url").asText("");
        return new ToolResult(toolCallId, fetch(url), null, null);
    }

    private String fetch(String url) {
        if (url == null || url.isBlank()) {
            return "抓取失败：url 参数为空";
        }
        final URI uri;
        try {
            uri = SafeUrlValidator.validatePublicHttpUrl(url.trim(), "网页抓取");
        } catch (Exception e) {
            log.warn("[AI工具] fetch_url URL 校验失败 url={}: {}", url, e.getMessage());
            return "抓取失败：" + e.getMessage();
        }
        try {
            Document doc = Jsoup.connect(uri.toString())
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(MAX_BODY_BYTES)
                    .userAgent("Mozilla/5.0 (compatible; StellarBot/1.0; +https://booksy.cf)")
                    .get();
            String title = doc.title() == null ? "" : doc.title().trim();
            String text = (doc.body() != null ? doc.body() : doc).text();
            if (text.length() > MAX_TEXT_CHARS) {
                text = text.substring(0, MAX_TEXT_CHARS) + "…(内容过长已截断)";
            }
            log.info("[AI工具] fetch_url 成功 url={} titleLen={} textLen={}", url, title.length(), text.length());
            return (title.isEmpty() ? "" : "网页标题：" + title + "\n") + "网页内容：\n" + text;
        } catch (Exception e) {
            log.warn("[AI工具] fetch_url 抓取失败 url={}: {}", url, e.getMessage());
            return "抓取失败：" + e.getMessage() + "。无法获取该网页内容，请基于已有信息继续完成任务。";
        }
    }
}
