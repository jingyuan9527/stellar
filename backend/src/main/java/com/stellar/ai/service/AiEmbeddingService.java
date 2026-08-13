package com.stellar.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.ai.vo.AiResolvedConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.stellar.infra.ExternalCallLogger;

/**
 * 向量化服务：调用 OpenAI 兼容 /v1/embeddings 接口，按 EMBEDDING 类型模型解析配置。
 * <p>支持单条与批量。失败抛 BusinessException，由调用方决定降级（如跳过向量化）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiEmbeddingService {

    private final AiModelService aiModelService;
    private final ObjectMapper objectMapper;
    private final ExternalCallLogger externalCallLogger;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 单条文本向量化。modelId 为空用 EMBEDDING 默认模型。
     */
    public float[] embed(String text, Long modelId) {
        return embedBatch(List.of(text), modelId).get(0);
    }

    /**
     * 批量文本向量化。input 为字符串列表，返回与输入等长的向量列表。
     */
    public List<float[]> embedBatch(List<String> texts, Long modelId) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        AiResolvedConfig cfg = modelId != null
                ? aiModelService.resolveConfig(modelId)
                : aiModelService.resolveDefaultConfig("EMBEDDING");
        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/embeddings";
        long start = System.currentTimeMillis();
        String callParams = "model=" + cfg.model() + ", providerId=" + cfg.providerId() + ", count=" + texts.size();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", cfg.model());
            body.put("input", texts);
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            log.info("[Embedding] 请求 model={} count={} providerId={}", cfg.model(), texts.size(), cfg.providerId());
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new BusinessException("Embedding 接口返回错误: HTTP " + response.statusCode()
                        + " " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode dataNode = json.path("data");
            if (dataNode.isMissingNode() || !dataNode.isArray()) {
                throw new BusinessException("Embedding 响应缺少 data 字段");
            }
            List<float[]> result = new ArrayList<>(texts.size());
            // 按 index 排序确保与输入顺序一致
            List<JsonNode> arr = new ArrayList<>();
            dataNode.forEach(arr::add);
            arr.sort((a, b) -> Integer.compare(a.path("index").asInt(0), b.path("index").asInt(0)));
            for (JsonNode item : arr) {
                JsonNode emb = item.path("embedding");
                if (!emb.isArray()) {
                    throw new BusinessException("Embedding 响应缺少 embedding 字段");
                }
                float[] vec = new float[emb.size()];
                for (int i = 0; i < emb.size(); i++) {
                    vec[i] = (float) emb.get(i).asDouble(0);
                }
                result.add(vec);
            }
            externalCallLogger.success("Embedding", url, callParams + ", resultCount=" + result.size()
                    + ", dim=" + (result.isEmpty() ? 0 : result.get(0).length),
                    System.currentTimeMillis() - start);
            log.info("[Embedding] 响应 count={} dim={}", result.size(),
                    result.isEmpty() ? 0 : result.get(0).length);
            return result;
        } catch (BusinessException e) {
            externalCallLogger.failure("Embedding", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            externalCallLogger.failure("Embedding", url, callParams, e.getMessage(),
                    System.currentTimeMillis() - start);
            log.error("[Embedding] 调用失败: {}", e.getMessage(), e);
            throw new BusinessException("向量化调用失败: " + e.getMessage());
        }
    }

    /**
     * 向量转 JSON 数组文本 '[v1,v2,...]'，用于 SQL 绑定。委托 {@link VectorOps}。
     */
    public String toVectorLiteral(float[] vec) {
        return VectorOps.toVectorLiteral(vec);
    }
}
