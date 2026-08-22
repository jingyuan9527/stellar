package com.stellar.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.vo.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link WebPageFetchTool} 单测：不发起真实网络请求，
 * 覆盖 schema 定义、参数解析失败与 URL 校验失败均转错误文本回传（不抛异常）。
 */
class WebPageFetchToolTest {

    private final WebPageFetchTool tool = new WebPageFetchTool(new ObjectMapper());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode toolCall(String arguments) {
        String json = "{\"id\":\"call-1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"fetch_url\",\"arguments\":%s}}"
                .formatted(arguments == null ? "null" : objectMapper.valueToTree(arguments).toString());
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void definition_含名称与url必填参数() {
        var def = tool.definition();

        assertEquals("function", def.get("type"));
        @SuppressWarnings("unchecked")
        var function = (java.util.Map<String, Object>) def.get("function");
        assertEquals(WebPageFetchTool.TOOL_NAME, function.get("name"));
        assertNotNull(function.get("description"));
        assertTrue(function.get("parameters").toString().contains("url"));
    }

    @Test
    void 参数为空_url缺失_返回错误文本() {
        ToolResult result = tool.execute(toolCall("{}"));

        assertEquals("call-1", result.toolCallId());
        assertTrue(result.content().startsWith("抓取失败"));
        assertNull(result.attachmentType());
    }

    @Test
    void 参数非合法JSON_返回错误文本() {
        ToolResult result = tool.execute(toolCall("不是json"));

        assertTrue(result.content().contains("参数"));
    }

    @Test
    void 私网地址_返回错误文本_不抛异常() {
        ToolResult result = tool.execute(toolCall("{\"url\":\"http://127.0.0.1:8080/admin\"}"));

        assertEquals("call-1", result.toolCallId());
        assertTrue(result.content().startsWith("抓取失败"));
    }

    @Test
    void 非http协议_返回错误文本() {
        ToolResult result = tool.execute(toolCall("{\"url\":\"ftp://example.com/file\"}"));

        assertTrue(result.content().startsWith("抓取失败"));
    }
}
