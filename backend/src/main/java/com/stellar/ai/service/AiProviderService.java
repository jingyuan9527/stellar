package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.AiProviderDTO;
import com.stellar.ai.entity.SysAiModel;
import com.stellar.ai.entity.SysAiProvider;
import com.stellar.ai.mapper.SysAiModelMapper;
import com.stellar.ai.mapper.SysAiProviderMapper;
import com.stellar.ai.vo.AiProviderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
     * 拉取供应商端点支持的模型列表（仅预览不落库），供前端弹窗勾选。
     * <p>不改库、不清缓存，与 saveSelectedModels 配对使用。
     */
    public List<String> previewModels(Long id) {
        SysAiProvider p = getRawById(id);
        if (!StringUtils.hasText(p.getEndpoint()) || !StringUtils.hasText(p.getApiKey())) {
            throw new BusinessException("请先配置该供应商的接口地址和 API Key");
        }
        log.info("previewModels: providerId={} name={} endpoint={} apiKey(masked)={}",
                id, p.getName(), p.getEndpoint(), maskApiKey(p.getApiKey()));
        try {
            List<String> models = fetchRemoteModels(p);
            log.info("previewModels: providerId={} 远端模型 {} 个", id, models.size());
            return models;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("获取模型列表失败: " + e.getMessage());
        }
    }

    /**
     * 覆盖式保存勾选的模型：先清空该供应商旧模型，再按勾选顺序写入（默认 TEXT 类型）。
     * <p>与弹窗所见一致，无残留；同时更新 available_models，清掉 ai-provider 与 ai-model 两份缓存。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "ai-provider", allEntries = true),
            @CacheEvict(cacheNames = "ai-model", allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public void saveSelectedModels(Long id, List<String> models) {
        SysAiProvider p = getRawById(id);
        // 过滤空白项并去重，保序
        List<String> selected = models == null ? List.of() : models.stream()
                .filter(m -> StringUtils.hasText(m))
                .map(String::trim)
                .distinct()
                .toList();
        modelMapper.delete(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getProviderId, id));
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < selected.size(); i++) {
            SysAiModel model = new SysAiModel();
            model.setProviderId(id);
            model.setModel(selected.get(i));
            model.setModelType("TEXT");
            model.setEnabled(1);
            model.setIsDefault(0);
            model.setSortOrder(i);
            model.setCreateTime(now);
            model.setUpdateTime(now);
            modelMapper.insert(model);
        }
        p.setAvailableModels(selected.isEmpty() ? "" : String.join(",", selected));
        p.setUpdateTime(now);
        providerMapper.updateById(p);
        log.info("[AI供应商] 覆盖保存 {} 个模型 providerId={} name={}", selected.size(), id, p.getName());
    }

    /**
     * 清空该供应商下全部模型并清空 available_models。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "ai-provider", allEntries = true),
            @CacheEvict(cacheNames = "ai-model", allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public void clearModels(Long id) {
        SysAiProvider p = getRawById(id);
        modelMapper.delete(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getProviderId, id));
        p.setAvailableModels("");
        p.setUpdateTime(LocalDateTime.now());
        providerMapper.updateById(p);
        log.info("[AI供应商] 清空全部模型 providerId={} name={}", id, p.getName());
    }

    /**
     * 请求远端 /v1/models 取模型 id 列表（已排序），不落库。
     */
    private List<String> fetchRemoteModels(SysAiProvider p) throws Exception {
        String url = p.getEndpoint().replaceAll("/+$", "") + "/v1/models";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + p.getApiKey())
                .GET()
                .build();
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
        return models;
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
