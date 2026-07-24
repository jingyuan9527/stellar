package com.stellar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.dto.AiConfigDTO;
import com.stellar.entity.SysAiConfig;
import com.stellar.mapper.SysAiConfigMapper;
import com.stellar.vo.AiConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigService {

    private final SysAiConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 获取配置（API Key 脱敏后返回）。
     */
    public AiConfigVO getConfig() {
        SysAiConfig config = getRawConfig();
        AiConfigVO vo = new AiConfigVO();
        vo.setEndpoint(config.getEndpoint());
        vo.setApiKey(maskApiKey(config.getApiKey()));
        vo.setModel(config.getModel());
        vo.setConchAiEnabled(config.getConchAiEnabled() == null ? 1 : config.getConchAiEnabled());
        vo.setConfigured(StringUtils.hasText(config.getEndpoint())
                && StringUtils.hasText(config.getApiKey())
                && StringUtils.hasText(config.getModel()));
        return vo;
    }

    /**
     * 更新配置（apiKey 为空时保留原值，避免脱敏回写覆盖）。
     */
    public void updateConfig(AiConfigDTO dto) {
        SysAiConfig config = getRawConfig();
        if (StringUtils.hasText(dto.getEndpoint())) {
            config.setEndpoint(dto.getEndpoint());
        }
        if (StringUtils.hasText(dto.getApiKey())) {
            config.setApiKey(dto.getApiKey());
        }
        if (StringUtils.hasText(dto.getModel())) {
            config.setModel(dto.getModel());
        }
        if (dto.getConchAiEnabled() != null) {
            config.setConchAiEnabled(dto.getConchAiEnabled());
        }
        config.setUpdateTime(LocalDateTime.now());
        configMapper.updateById(config);
    }

    /**
     * 拉取 LLM 端点支持的模型列表。优先用前端传入配置，未传则回退数据库已保存配置。
     */
    public List<String> fetchModels(String endpoint, String apiKey) {
        String ep = StringUtils.hasText(endpoint) ? endpoint : null;
        String key = StringUtils.hasText(apiKey) ? apiKey : null;
        if (ep == null || key == null) {
            SysAiConfig config = getRawConfig();
            if (ep == null) ep = config.getEndpoint();
            if (key == null) key = config.getApiKey();
        }
        if (!StringUtils.hasText(ep) || !StringUtils.hasText(key)) {
            throw new BusinessException("请先配置接口地址和 API Key");
        }
        String url = ep.replaceAll("/+$", "") + "/v1/models";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + key)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BusinessException("获取模型列表失败: HTTP " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode dataNode = json.path("data");
            List<String> models = new ArrayList<>();
            if (dataNode.isArray()) {
                for (JsonNode node : dataNode) {
                    String id = node.path("id").asText("");
                    if (!id.isEmpty()) models.add(id);
                }
            }
            Collections.sort(models);
            return models;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("获取模型列表失败: " + e.getMessage());
        }
    }

    /**
     * 测试 LLM 端点连通性（发一条 max_tokens=1 的请求）。优先用前端传入配置。
     */
    public void testConnection(String endpoint, String apiKey, String model) {
        String ep = StringUtils.hasText(endpoint) ? endpoint : null;
        String key = StringUtils.hasText(apiKey) ? apiKey : null;
        String mdl = StringUtils.hasText(model) ? model : null;
        if (ep == null || key == null || mdl == null) {
            SysAiConfig config = getRawConfig();
            if (ep == null) ep = config.getEndpoint();
            if (key == null) key = config.getApiKey();
            if (mdl == null) mdl = config.getModel();
        }
        if (!StringUtils.hasText(ep) || !StringUtils.hasText(key) || !StringUtils.hasText(mdl)) {
            throw new BusinessException("请先配置接口地址、API Key 和模型名称");
        }
        String url = ep.replaceAll("/+$", "") + "/v1/chat/completions";

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", mdl);
            body.put("messages", List.of(Map.of("role", "user", "content", "hi")));
            body.put("max_tokens", 1);
            body.put("stream", false);
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BusinessException("连通失败: HTTP " + response.statusCode());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("连通失败: " + e.getMessage());
        }
    }

    /**
     * 获取原始配置（不存在则自动初始化空行）。
     */
    public SysAiConfig getRawConfig() {
        List<SysAiConfig> configs = configMapper.selectList(null);
        if (configs.isEmpty()) {
            SysAiConfig config = new SysAiConfig();
            config.setEndpoint("");
            config.setApiKey("");
            config.setModel("");
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
            configMapper.insert(config);
            return config;
        }
        return configs.get(0);
    }

    private String maskApiKey(String key) {
        if (!StringUtils.hasText(key)) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
