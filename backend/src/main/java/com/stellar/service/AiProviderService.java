package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.dto.AiProviderDTO;
import com.stellar.entity.SysAiModel;
import com.stellar.entity.SysAiProvider;
import com.stellar.mapper.SysAiModelMapper;
import com.stellar.mapper.SysAiProviderMapper;
import com.stellar.vo.AiProviderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 供应商服务：多供应商 CRUD + 拉取模型列表 + 连通测试。
 * <p>API Key 返回前端时脱敏；更新时为空保留原值，避免脱敏回写覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderService {

    private final SysAiProviderMapper providerMapper;
    private final SysAiModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 查全部供应商（apiKey 脱敏），按 sort_order 升序。后台配置页首屏读，走 Spring Cache。
     */
    @Cacheable(cacheNames = "ai-provider", key = "'all'")
    public List<AiProviderVO> list() {
        List<SysAiProvider> list = providerMapper.selectList(
                new LambdaQueryWrapper<SysAiProvider>()
                        .orderByAsc(SysAiProvider::getSortOrder)
                        .orderByAsc(SysAiProvider::getId));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 取原始供应商（含 apiKey），供调用方发起 LLM 请求。不存在抛异常。
     */
    public SysAiProvider getRawById(Long id) {
        SysAiProvider p = providerMapper.selectById(id);
        if (p == null) {
            throw new BusinessException("AI 供应商不存在: id=" + id);
        }
        return p;
    }

    @CacheEvict(cacheNames = "ai-provider", allEntries = true)
    public void create(AiProviderDTO dto) {
        SysAiProvider p = new SysAiProvider();
        p.setName(dto.getName().trim());
        p.setEndpoint(dto.getEndpoint().trim());
        p.setApiKey(StringUtils.hasText(dto.getApiKey()) ? dto.getApiKey().trim() : "");
        p.setAvailableModels("");
        p.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        p.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        providerMapper.insert(p);
        log.info("[AI供应商] 新增 id={} name={}", p.getId(), p.getName());
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "ai-provider", allEntries = true),
            @CacheEvict(cacheNames = "ai-model", allEntries = true)
    })
    public void update(AiProviderDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("供应商 id 不能为空");
        }
        SysAiProvider exist = providerMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException("供应商不存在: id=" + dto.getId());
        }
        exist.setName(dto.getName().trim());
        exist.setEndpoint(dto.getEndpoint().trim());
        // apiKey 为空保留原值，避免脱敏回写覆盖
        if (StringUtils.hasText(dto.getApiKey())) {
            exist.setApiKey(dto.getApiKey().trim());
        }
        if (dto.getEnabled() != null) {
            exist.setEnabled(dto.getEnabled());
        }
        if (dto.getSortOrder() != null) {
            exist.setSortOrder(dto.getSortOrder());
        }
        exist.setUpdateTime(LocalDateTime.now());
        providerMapper.updateById(exist);
        log.info("[AI供应商] 更新 id={} name={}", exist.getId(), exist.getName());
    }

    /**
     * 删除供应商，级联删除其下模型。同时清掉 ai-provider 与 ai-model 两份缓存。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "ai-provider", allEntries = true),
            @CacheEvict(cacheNames = "ai-model", allEntries = true)
    })
    public void delete(Long id) {
        if (providerMapper.selectById(id) == null) {
            throw new BusinessException("供应商不存在: id=" + id);
        }
        modelMapper.delete(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getProviderId, id));
        providerMapper.deleteById(id);
        log.info("[AI供应商] 删除 id={}（含其下模型）", id);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "ai-provider", allEntries = true),
            @CacheEvict(cacheNames = "ai-model", allEntries = true)
    })
    public void toggleEnabled(Long id, Integer enabled) {
        SysAiProvider exist = providerMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("供应商不存在: id=" + id);
        }
        exist.setEnabled(enabled);
        exist.setUpdateTime(LocalDateTime.now());
        providerMapper.updateById(exist);
        log.info("[AI供应商] 切换启用 id={} enabled={}", id, enabled);
    }

    /**
     * 拉取供应商端点支持的模型列表，落库 available_models，并同步创建未存在的模型记录。
     * <p>会改 available_models 与 sys_ai_model，同时清掉 ai-provider 与 ai-model 两份缓存。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "ai-provider", allEntries = true),
            @CacheEvict(cacheNames = "ai-model", allEntries = true)
    })
    public List<String> fetchModels(Long id) {
        SysAiProvider p = getRawById(id);
        if (!StringUtils.hasText(p.getEndpoint()) || !StringUtils.hasText(p.getApiKey())) {
            throw new BusinessException("请先配置该供应商的接口地址和 API Key");
        }
        log.info("fetchModels: providerId={} name={} endpoint={} apiKey(masked)={}",
                id, p.getName(), p.getEndpoint(), maskApiKey(p.getApiKey()));
        String url = p.getEndpoint().replaceAll("/+$", "") + "/v1/models";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + p.getApiKey())
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
                    String mid = node.path("id").asText("");
                    if (!mid.isEmpty()) models.add(mid);
                }
            }
            Collections.sort(models);
            // 持久化到供应商的 available_models
            p.setAvailableModels(models.isEmpty() ? "" : String.join(",", models));
            p.setUpdateTime(LocalDateTime.now());
            providerMapper.updateById(p);
            // 同步创建未存在的模型记录（默认 TEXT 类型），拉取后模型管理立即可见
            int created = syncModels(p.getId(), models);
            if (created > 0) {
                log.info("[AI供应商] 同步创建 {} 个新模型 providerId={}", created, p.getId());
            }
            return models;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("获取模型列表失败: " + e.getMessage());
        }
    }

    /**
     * 同步拉取到的模型到 sys_ai_model：未存在的创建（默认 TEXT 类型），已存在的保留不动。
     * <p>避免覆盖用户已设的类型/启停/默认。
     * @return 新创建的模型数量
     */
    private int syncModels(Long providerId, List<String> models) {
        if (models.isEmpty()) {
            return 0;
        }
        List<SysAiModel> existing = modelMapper.selectList(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getProviderId, providerId));
        Set<String> existingNames = existing.stream()
                .map(SysAiModel::getModel).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        int order = existing.size();
        int created = 0;
        for (String m : models) {
            if (existingNames.contains(m)) {
                continue;
            }
            SysAiModel model = new SysAiModel();
            model.setProviderId(providerId);
            model.setModel(m);
            model.setModelType("TEXT");
            model.setEnabled(1);
            model.setIsDefault(0);
            model.setSortOrder(order++);
            model.setCreateTime(now);
            model.setUpdateTime(now);
            modelMapper.insert(model);
            created++;
        }
        return created;
    }

    /**
     * 测试供应商连通性（发一条 max_tokens=1 的请求）。model 可选，未传则取该供应商下任一模型。
     */
    public void testConnection(Long id, String model) {
        SysAiProvider p = getRawById(id);
        if (!StringUtils.hasText(p.getEndpoint()) || !StringUtils.hasText(p.getApiKey())) {
            throw new BusinessException("请先配置该供应商的接口地址和 API Key");
        }
        String mdl = StringUtils.hasText(model) ? model : null;
        if (mdl == null) {
            SysAiModel m = modelMapper.selectOne(new LambdaQueryWrapper<SysAiModel>()
                    .eq(SysAiModel::getProviderId, id)
                    .eq(SysAiModel::getEnabled, 1)
                    .last("LIMIT 1"));
            if (m != null) {
                mdl = m.getModel();
            }
        }
        if (!StringUtils.hasText(mdl)) {
            throw new BusinessException("请先指定或配置模型名称");
        }
        log.info("testConnection: providerId={} endpoint={} apiKey(masked)={} model={}",
                id, p.getEndpoint(), maskApiKey(p.getApiKey()), mdl);
        String url = p.getEndpoint().replaceAll("/+$", "") + "/v1/chat/completions";

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
                    .header("Authorization", "Bearer " + p.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String respBody = response.body();
            if (status != 200) {
                log.warn("LLM 连通测试失败: status={}, providerId={}, model={}, body={}",
                        status, id, mdl, respBody);
                String detail = StringUtils.hasText(respBody) ? respBody.trim() : "";
                if (detail.length() > 300) {
                    detail = detail.substring(0, 300) + "...";
                }
                throw new BusinessException("连通失败: HTTP " + status
                        + (detail.isEmpty() ? "" : " - " + detail));
            }
            log.info("LLM 连通测试成功: providerId={}, model={}", id, mdl);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("连通失败: " + e.getMessage());
        }
    }

    private AiProviderVO toVO(SysAiProvider p) {
        AiProviderVO vo = new AiProviderVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setEndpoint(p.getEndpoint());
        vo.setApiKey(maskApiKey(p.getApiKey()));
        vo.setAvailableModels(parseAvailableModels(p.getAvailableModels()));
        vo.setEnabled(p.getEnabled());
        vo.setSortOrder(p.getSortOrder());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        return vo;
    }

    private String maskApiKey(String key) {
        if (!StringUtils.hasText(key)) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private List<String> parseAvailableModels(String raw) {
        if (!StringUtils.hasText(raw)) return Collections.emptyList();
        return Arrays.asList(raw.split(","));
    }
}
