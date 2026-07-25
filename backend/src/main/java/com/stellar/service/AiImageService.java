package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.entity.SysFile;
import com.stellar.mapper.SysFileMapper;
import com.stellar.vo.AiImageResultVO;
import com.stellar.vo.AiResolvedConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 图片生成服务：调 /v1/images/generations，b64_json 解码后存 sys_file 永久化。
 * <p>图片生成 API 不返回 token usage，按 prompt 字符估算记录（source=estimate）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageService {

    private final AiModelService aiModelService;
    private final SysFileMapper fileMapper;
    private final SysAiUsageService sysAiUsageService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public AiImageResultVO generate(Long modelId, String prompt, String size) {
        AiResolvedConfig cfg = aiModelService.resolveConfig(modelId);
        if (!"IMAGE".equals(cfg.modelType())) {
            throw new BusinessException("该模型不是图片生成类型，请选择 IMAGE 类型模型");
        }

        String url = cfg.endpoint().replaceAll("/+$", "") + "/v1/images/generations";
        String sz = StringUtils.hasText(size) ? size.trim() : "1024x1024";

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", cfg.model());
            body.put("prompt", prompt);
            body.put("n", 1);
            body.put("size", sz);
            // 要求返回 b64，避免临时 URL 失效，落库永久保存
            body.put("response_format", "b64_json");
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            log.info("AI 图片生成请求: model={}, providerId={}, size={}, promptLen={}",
                    cfg.model(), cfg.providerId(), sz, prompt.length());

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("AI 图片生成失败: status={}, providerId={}, body={}",
                        response.statusCode(), cfg.providerId(), response.body());
                String detail = StringUtils.hasText(response.body()) ? response.body().trim() : "";
                if (detail.length() > 300) {
                    detail = detail.substring(0, 300) + "...";
                }
                throw new BusinessException("图片生成失败: HTTP " + response.statusCode()
                        + (detail.isEmpty() ? "" : " - " + detail));
            }

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode dataNode = json.path("data");
            if (!dataNode.isArray() || dataNode.isEmpty()) {
                throw new BusinessException("图片生成失败: 返回数据为空");
            }
            String b64 = dataNode.path(0).path("b64_json").asText("");
            if (b64.isEmpty()) {
                // 部分供应商返回 url 而非 b64，兜底提示
                JsonNode urlNode = dataNode.path(0).path("url");
                if (!urlNode.isMissingNode() && StringUtils.hasText(urlNode.asText())) {
                    throw new BusinessException("供应商返回 URL 而非 b64_json，请确认模型配置");
                }
                throw new BusinessException("图片生成失败: 未返回图片数据");
            }

            byte[] imageBytes = Base64.getDecoder().decode(b64);

            // 存 sys_file 永久化，游客可经 GET /file/{id} 读取
            SysFile file = new SysFile();
            String name = prompt.length() > 20 ? prompt.substring(0, 20) : prompt;
            file.setOriginalName(name + ".png");
            file.setExt("png");
            file.setContentType("image/png");
            file.setSize((long) imageBytes.length);
            file.setData(imageBytes);
            file.setCreateTime(LocalDateTime.now());
            fileMapper.insert(file);

            // token 估算记录（图片 API 无 usage）
            int promptTokens = estimateTokens(prompt);
            sysAiUsageService.record(getSubjectType(), getSubjectId(),
                    cfg.providerId(), cfg.model(), cfg.modelType(),
                    promptTokens, 0, promptTokens, "estimate");

            AiImageResultVO vo = new AiImageResultVO();
            vo.setFileId(file.getId());
            vo.setUrl("/file/" + file.getId());
            log.info("AI 图片生成成功: fileId={}, size={}", file.getId(), imageBytes.length);
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 图片生成异常: {}", e.getMessage(), e);
            throw new BusinessException("图片生成失败: " + e.getMessage());
        }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length();
    }

    private String getSubjectType() {
        return StpUtil.isLogin() ? "account" : "ip";
    }

    private String getSubjectId() {
        if (StpUtil.isLogin()) {
            return StpUtil.getLoginIdAsString();
        }
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String ip = req.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                ip = ip.split(",")[0].trim();
            }
            if (ip == null || ip.isBlank()) {
                ip = req.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isBlank()) {
                ip = req.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
