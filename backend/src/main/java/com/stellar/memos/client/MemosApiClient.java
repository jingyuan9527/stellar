package com.stellar.memos.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Memos API 客户端：Connect unary JSON（优先）+ REST 兜底双通道访问 memo.booksy.cf。
 * <p>参考 memos-back 目录下 restore 脚本已实战验证的通道：
 * Connect（POST {base}/memos.api.v1.MemoService/Xxx，Content-Type: application/json +
 * Connect-Protocol-Version: 1）为主，REST（{base}/api/v1/memos）兜底。
 * <p>仅封装模块内需要的操作：全量分页拉取（ListMemos）、更新 content（UpdateMemo，
 * 标签写回 = 在 content 末尾追加 #标签）。
 */
@Slf4j
@Component
public class MemosApiClient {

    /** 分页大小：一次拉取条数（远端默认上限 100） */
    private static final int PAGE_SIZE = 100;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public MemosApiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 可从外部注入（测试用）。 */
    public MemosApiClient(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /** 远端某条笔记的最小信息（拉取后 upsert 本地用）。 */
    public record MemosRemoteMemo(String uid, String content,
                                  LocalDateTime createTime, LocalDateTime updateTime,
                                  List<String> tags) {
    }

    /**
     * 从 webhook payload 的 memo 对象解析远端笔记（与列表解析同构字段）。
     * 缺 uid 时返回 null，由调用方跳过。
     */
    public MemosRemoteMemo parseMemo(JsonNode memo) {
        if (memo == null || memo.isNull()) {
            return null;
        }
        String uid = resolveUid(memo);
        if (uid.isBlank()) {
            return null;
        }
        return new MemosRemoteMemo(uid,
                memo.path("content").asText(""),
                parseTime(memo.path("createTime").asText(null)),
                parseTime(memo.path("updateTime").asText(null)),
                parseTags(memo));
    }

    /**
     * 全量分页拉取远端活跃笔记（ListMemos 默认只回 ACTIVE）。
     */
    public List<MemosRemoteMemo> listAllMemos(String baseUrl, String token) {
        List<MemosRemoteMemo> all = new ArrayList<>();
        String pageToken = "";
        do {
            JsonNode resp;
            try {
                resp = connectPost(baseUrl, token, "ListMemos",
                        connectBody("pageSize", String.valueOf(PAGE_SIZE), "pageToken", pageToken));
                log.debug("[备忘同步] ListMemos Connect 成功 pageToken={}", pageToken);
            } catch (Exception e) {
                log.warn("[备忘同步] ListMemos Connect 通道失败，改走 REST: {}", e.getMessage());
                resp = restList(baseUrl, token, pageToken);
            }
            JsonNode memos = resp.path("memos");
            if (memos.isArray()) {
                for (JsonNode m : memos) {
                    String uid = resolveUid(m);
                    if (uid.isBlank()) {
                        log.warn("[备忘同步] 远端 memos 缺少 uid/name，跳过 contentLen={}",
                                m.path("content").asText("").length());
                        continue;
                    }
                    all.add(new MemosRemoteMemo(
                            uid,
                            m.path("content").asText(""),
                            parseTime(m.path("createTime").asText(null)),
                            parseTime(m.path("updateTime").asText(null)),
                            parseTags(m)));
                    log.debug("[备忘同步] 远端 memos 解析 uid={} contentLen={} tags={}",
                            uid, m.path("content").asText("").length(), parseTags(m));
                }
            }
            pageToken = resp.path("nextPageToken").asText("");
            log.debug("[备忘同步] ListMemos 页码完成 count={} nextPageToken={}", memos.size(), pageToken);
        } while (!pageToken.isBlank());
        log.info("[备忘同步] 远端拉取完成 total={}", all.size());
        return all;
    }

    /**
     * 更新远端笔记 content（标签写回：content 末尾追加 #标签）。
     * <p>Connect UpdateMemo 优先，REST PATCH 兜底；两通道都失败抛异常由调用方统计失败数。
     */
    public void updateContent(String baseUrl, String token, String uid, String newContent) {
        String body = "{\"memo\":{\"name\":\"memos/" + uid + "\",\"content\":"
                + jsonString(newContent)
                + ",\"visibility\":\"PRIVATE\"},\"updateMask\":{\"paths\":[\"content\"]}}";
        try {
            connectPost(baseUrl, token, "UpdateMemo", body);
            return;
        } catch (Exception e) {
            log.warn("[备忘同步] UpdateMemo Connect 通道失败，改走 REST: {}", e.getMessage());
        }
        // REST 兜底：PATCH /api/v1/memos/{uid}?updateMask.paths=content（兼容 grpc-gateway 两种绑定风格）
        BasicResult result = restPatch(baseUrl, token, uid, newContent);
        if (!result.ok()) {
            throw new BusinessException("Memos 更新失败: HTTP " + result.status()
                    + (result.body() != null && !result.body().isBlank()
                        ? " body=" + result.body().substring(0, Math.min(200, result.body().length())) : ""));
        }
    }

    /** Connect unary POST（JSON 协议），返回响应 JsonNode。 */
    private JsonNode connectPost(String baseUrl, String token, String method, String body) throws IOException, InterruptedException {
        String url = baseUrl + "/memos.api.v1.MemoService/" + method;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Connect-Protocol-Version", "1")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new BusinessException("Memos Connect " + method + " 失败: HTTP " + resp.statusCode()
                    + (resp.body() != null && !resp.body().isBlank()
                        ? " body=" + resp.body().substring(0, Math.min(200, resp.body().length())) : ""));
        }
        return objectMapper.readTree(resp.body());
    }

    /** REST GET ListMemos 兜底。 */
    private JsonNode restList(String baseUrl, String token, String pageToken) {
        String url = baseUrl + "/api/v1/memos?pageSize=" + PAGE_SIZE + "&pageToken=" + encode(pageToken);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new BusinessException("Memos REST 列表失败: HTTP " + resp.statusCode());
            }
            return objectMapper.readTree(resp.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Memos 拉取失败: " + e.getMessage());
        }
    }

    /** REST PATCH 更新 content 兜底；返回状态码与响应体，由调用方判定结果。 */
    private BasicResult restPatch(String baseUrl, String token, String uid, String newContent) {
        String url = baseUrl + "/api/v1/memos/" + encode(uid) + "?updateMask.paths=content";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(
                        "{\"name\":\"memos/" + uid + "\",\"content\":" + jsonString(newContent) + "}",
                        StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new BasicResult(resp.statusCode() == 200, resp.statusCode(),
                    resp.body() == null ? "" : resp.body());
        } catch (Exception e) {
            return new BasicResult(false, 0, e.getMessage());
        }
    }

    /** 解析 memo 的 uid：优先 uid 字段（旧版），v0.30+ 无 uid 字段，从 name（memos/{uid}）提取。 */
    private String resolveUid(JsonNode memo) {
        String uid = memo.path("uid").asText("").trim();
        if (!uid.isBlank()) {
            return uid;
        }
        String name = memo.path("name").asText("").trim();
        if (name.startsWith("memos/")) {
            return name.substring("memos/".length()).trim();
        }
        return "";
    }

    /** 解析远端 tags：Memo JSON 顶层 tags 或 property.tags 均可。 */
    private List<String> parseTags(JsonNode memo) {
        JsonNode tags = memo.path("tags");
        if (!tags.isArray()) {
            tags = memo.path("property").path("tags");
        }
        List<String> result = new ArrayList<>();
        if (tags.isArray()) {
            for (JsonNode t : tags) {
                String v = t.asText("").trim();
                if (!v.isBlank()) {
                    result.add(v);
                }
            }
        }
        return result;
    }

    private LocalDateTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private String jsonString(String v) {
        try {
            return objectMapper.writeValueAsString(v == null ? "" : v);
        } catch (Exception e) {
            String escaped = v.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
            return "\"" + escaped + "\"";
        }
    }

    private static String encode(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    /** 轻量 JSON 键值对构建（列表页请求体用），值统一按 JSON 字符串转义加引号。 */
    static String connectBody(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(kv[i]).append("\":");
            String v = kv[i + 1] == null ? "" : kv[i + 1];
            sb.append('"').append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    /** REST 兜底结果。 */
    private record BasicResult(boolean ok, int status, String body) {
    }
}
