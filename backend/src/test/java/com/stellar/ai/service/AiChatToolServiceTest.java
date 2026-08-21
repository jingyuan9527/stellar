package com.stellar.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.service.AiChatToolService;
import com.stellar.ai.service.AiImageService;
import com.stellar.ai.service.AiModelService;
import com.stellar.ai.vo.AiResolvedConfig;
import com.stellar.ai.vo.ToolResult;
import com.stellar.common.BusinessException;
import com.stellar.system.service.SysSettingService;
import com.stellar.tts.service.AiTtsService;
import com.stellar.tts.service.TtsRecordService;
import com.stellar.tts.service.TtsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiChatToolService} 单测：8 个协作者构造注入（ObjectMapper 真实），覆盖工具 schema 暴露、
 * execute 分发（generate_image / synthesize_speech / 未知 / 参数解析失败）、图片生成的空值与成功、
 * 语音合成的 Edge/AI 双引擎选择与 AI 失败降级、以及 isEdgeVoice 正则 / decideEdgeEngine / resolveVoice 决策。
 */
@ExtendWith(MockitoExtension.class)
class AiChatToolServiceTest {

    @Mock
    AiImageService aiImageService;
    @Mock
    AiTtsService aiTtsService;
    @Mock
    TtsService ttsService;
    @Mock
    TtsRecordService ttsRecordService;
    @Mock
    AiModelService aiModelService;
    @Mock
    SysSettingService sysSettingService;
    @Mock
    com.stellar.system.service.FileService fileService;

    AiChatToolService service;
    final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = new AiChatToolService(aiImageService, aiTtsService, ttsService, ttsRecordService,
                aiModelService, sysSettingService, fileService, objectMapper);
    }

    private JsonNode toolCall(String name, String argsJson) {
        var root = objectMapper.createObjectNode();
        root.put("id", "c1");
        var fn = objectMapper.createObjectNode();
        fn.put("name", name);
        fn.put("arguments", argsJson);
        root.set("function", fn);
        return root;
    }

    // ===== getToolDefinitions =====

    @Test
    void getToolDefinitions_IMAGE不可用_仅暴露语音() {
        when(aiModelService.listEnabledByType("IMAGE")).thenReturn(List.of());
        List<Map<String, Object>> tools = service.getToolDefinitions();
        assertEquals(1, tools.size());
        assertEquals("synthesize_speech", ((Map<?, ?>) tools.get(0).get("function")).get("name"));
    }

    @Test
    void getToolDefinitions_IMAGE可用_暴露双工具() {
        var vo = new com.stellar.ai.vo.AiModelVO();
        when(aiModelService.listEnabledByType("IMAGE")).thenReturn(List.of(vo));
        List<Map<String, Object>> tools = service.getToolDefinitions();
        assertEquals(2, tools.size());
    }

    // ===== execute 分发 =====

    @Test
    void execute_generateImage_正常_调图片同步() {
        when(aiImageService.generateImageSync("a cat", "account", "u1")).thenReturn(99L);
        ToolResult r = service.execute(toolCall("generate_image", "{\"prompt\":\"a cat\"}"), null, "account", "u1");
        assertEquals("image", r.attachmentType());
        verify(aiImageService).generateImageSync("a cat", "account", "u1");
    }

    @Test
    void execute_generateImage_prompt空_错误() {
        ToolResult r = service.execute(toolCall("generate_image", "{}"), null, "account", "u1");
        assertTrue(r.content().contains("prompt 不能为空"));
        assertNull(r.attachmentType());
    }

    @Test
    void execute_synthesizeSpeech_text空_错误() {
        ToolResult r = service.execute(toolCall("synthesize_speech", "{}"), null, "account", "u1");
        assertTrue(r.content().contains("text 不能为空"));
    }

    @Test
    void execute_synthesizeSpeech_Edge引擎_调EdgeTts() {
        when(sysSettingService.get("chat_tts_engine", "ai")).thenReturn("edge");
        when(ttsService.synthesize(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble())).thenReturn(new byte[]{1});
        ToolResult r = service.execute(toolCall("synthesize_speech", "{\"text\":\"hi\"}"), null, "account", "u1");
        assertEquals("audio", r.attachmentType());
        verify(ttsService).synthesize(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void execute_synthesizeSpeech_AI引擎_成功() {
        when(sysSettingService.get("chat_tts_engine", "ai")).thenReturn("ai");
        when(aiModelService.resolveDefaultConfig("AUDIO")).thenReturn(new AiResolvedConfig(1L, 1L, "ep", "k", "m", "AUDIO"));
        when(aiTtsService.synthesize(anyLong(), anyString(), anyString(), any())).thenReturn(new byte[]{2});
        ToolResult r = service.execute(toolCall("synthesize_speech", "{\"text\":\"hi\"}"), null, "account", "u1");
        assertEquals("audio", r.attachmentType());
        verify(aiTtsService).synthesize(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void execute_synthesizeSpeech_AI失败_降级Edge() {
        when(sysSettingService.get("chat_tts_engine", "ai")).thenReturn("ai");
        when(aiModelService.resolveDefaultConfig("AUDIO")).thenReturn(new AiResolvedConfig(1L, 1L, "ep", "k", "m", "AUDIO"));
        when(aiTtsService.synthesize(anyLong(), anyString(), anyString(), any())).thenThrow(new BusinessException("AI 不可用"));
        when(ttsService.synthesize(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble())).thenReturn(new byte[]{3});
        ToolResult r = service.execute(toolCall("synthesize_speech", "{\"text\":\"hi\"}"), null, "account", "u1");
        assertEquals("audio", r.attachmentType());
        verify(ttsService).synthesize(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void execute_未知工具_错误() {
        ToolResult r = service.execute(toolCall("foo", "{}"), null, "account", "u1");
        assertTrue(r.content().contains("未知工具"));
    }

    @Test
    void execute_参数解析失败_错误() {
        ToolResult r = service.execute(toolCall("generate_image", "not-json"), null, "account", "u1");
        assertTrue(r.content().contains("参数解析失败"));
    }

    // ===== 引擎/音色决策（纯逻辑） =====

    @Test
    void decideEdgeEngine_指定Edge音色_true() {
        assertTrue(service.getToolDefinitions().size() >= 1); // 触发一次覆盖后，直接验证决策分支
        // 通过 synthesize_speech 指定 edge 音色走 edge 引擎
        when(ttsService.synthesize(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble())).thenReturn(new byte[]{1});
        ToolResult r = service.execute(toolCall("synthesize_speech", "{\"text\":\"hi\"}"), "zh-CN-XiaoxiaoNeural", "account", "u1");
        assertEquals("audio", r.attachmentType());
        verify(ttsService).synthesize(anyString(), eq("zh-CN-XiaoxiaoNeural"), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void decideEdgeEngine_指定MiMo音色_false() {
        when(aiModelService.resolveDefaultConfig("AUDIO")).thenReturn(new AiResolvedConfig(1L, 1L, "ep", "k", "m", "AUDIO"));
        when(aiTtsService.synthesize(anyLong(), anyString(), anyString(), any())).thenReturn(new byte[]{2});
        ToolResult r = service.execute(toolCall("synthesize_speech", "{\"text\":\"hi\"}"), "mimo_default", "account", "u1");
        assertEquals("audio", r.attachmentType());
        verify(aiTtsService).synthesize(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void decideEdgeEngine_系统开关edge_true() {
        when(sysSettingService.get("chat_tts_engine", "ai")).thenReturn("edge");
        when(ttsService.synthesize(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble())).thenReturn(new byte[]{1});
        ToolResult r = service.execute(toolCall("synthesize_speech", "{\"text\":\"hi\"}"), null, "account", "u1");
        assertEquals("audio", r.attachmentType());
    }
}
