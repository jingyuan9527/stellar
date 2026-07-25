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
import java.util.Arrays;
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
        vo.setAvailableModels(parseAvailableModels(config.getAvailableModels()));
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
        log.info("fetchModels: endpoint={}, apiKey(masked)={}", ep, maskApiKey(key));
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
            // 持久化拉取到的模型列表，供前端切换选择直到下次重新拉取
            saveAvailableModels(models);
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
        log.info("testConnection: endpoint={}, apiKey(masked)={}, model={}", ep, maskApiKey(key), mdl);
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
            int status = response.statusCode();
            String respBody = response.body();
            if (status != 200) {
                // LLM 返回非 200，记录响应体以便排查 401/4xx 的真实原因（key 越权、模型不可用等）
                log.warn("LLM 连通测试失败: status={}, url={}, model={}, body={}", status, url, mdl, respBody);
                String detail = StringUtils.hasText(respBody) ? respBody.trim() : "";
                if (detail.length() > 300) {
                    detail = detail.substring(0, 300) + "...";
                }
                throw new BusinessException("连通失败: HTTP " + status
                        + (detail.isEmpty() ? "" : " - " + detail));
            }
            log.info("LLM 连通测试成功: url={}, model={}", url, mdl);
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

    /** 把逗号分隔的 available_models 字段解析为列表，空则返回空列表。 */
    private List<String> parseAvailableModels(String raw) {
        if (!StringUtils.hasText(raw)) return Collections.emptyList();
        return Arrays.asList(raw.split(","));
    }

    /** 把拉取到的模型列表落库（逗号分隔），覆盖旧值。 */
    private void saveAvailableModels(List<String> models) {
        try {
            SysAiConfig config = getRawConfig();
            config.setAvailableModels(models.isEmpty() ? "" : String.join(",", models));
            config.setUpdateTime(LocalDateTime.now());
            configMapper.updateById(config);
        } catch (Exception e) {
            // 落库失败不影响拉取结果返回
            log.warn("保存可用模型列表失败: {}", e.getMessage(), e);
        }
    }
}
