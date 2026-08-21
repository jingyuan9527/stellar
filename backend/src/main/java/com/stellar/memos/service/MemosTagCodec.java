package com.stellar.memos.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Memos 标签文本处理：content 末尾标签块剥离/追加、LLM 输出解析、标签规范化与逗号串互转。
 * <p>从 MemosService 抽出的纯文本工具集（唯一有状态依赖是 JSON 解析）。
 */
@Component
public class MemosTagCodec {

    /** 匹配 content 末尾的 #标签 块（标签写回后远端回读会带上，入库时剥离保持原文纯净） */
    private static final Pattern TRAILING_TAG_BLOCK = Pattern.compile("(?s)(?:\\s*#[^\\s#]+)+$");

    private final ObjectMapper objectMapper;

    public MemosTagCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 剥离 content 末尾的 #标签 块，保持备份原文纯净。 */
    public static String stripTrailingTagBlock(String content) {
        if (content == null || content.isBlank()) {
            return content == null ? "" : content;
        }
        Matcher m = TRAILING_TAG_BLOCK.matcher(content);
        if (m.find()) {
            String stripped = m.replaceFirst("");
            return stripped.isBlank() ? "" : stripped;
        }
        return content;
    }

    /** 解析 LLM 输出为标签列表（按标点/空白分隔，去 #、去空、去重）。 */
    public List<String> parseTagsFromText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // 兼容纯 JSON 数组输出（如 ["a","b"]）
        String cleaned = text.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            try {
                List<String> arr = objectMapper.readValue(cleaned,
                        new TypeReference<List<String>>() {
                        });
                return arr.stream().map(MemosTagCodec::sanitizeTag).filter(StringUtils::hasText)
                        .distinct().limit(8).toList();
            } catch (Exception ignored) {
                // 非 JSON 按分隔符解析
            }
        }
        return Arrays.stream(cleaned.split("[,，、;；\\n\\r\\t ]+"))
                .map(MemosTagCodec::sanitizeTag)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(8)
                .toList();
    }

    /** 标签规范化：去 # 前缀、内部空白转下划线、截断。 */
    public static String sanitizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        String t = tag.trim().replaceAll("^#+", "").trim();
        if (t.isEmpty()) {
            return "";
        }
        t = t.replaceAll("[\\s]+", "_");
        return t.length() > 30 ? t.substring(0, 30) : t;
    }

    /** tags 逗号串 → 有序去重集合。 */
    public static Set<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(tags.split(","))
                .map(MemosTagCodec::sanitizeTag)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 有序去重集合 → 逗号串。 */
    public static String joinTags(java.util.Collection<String> tags) {
        return tags.stream().map(MemosTagCodec::sanitizeTag).filter(StringUtils::hasText)
                .distinct().collect(Collectors.joining(","));
    }

    /** 收集 content 中已有的 #标签（去 # 规范化）。 */
    public static Set<String> collectTagsInContent(String content) {
        Set<String> tags = new HashSet<>();
        if (!StringUtils.hasText(content)) {
            return tags;
        }
        Matcher m = Pattern.compile("#([^\\s#]+)").matcher(content);
        while (m.find()) {
            String t = sanitizeTag(m.group(1));
            if (!t.isEmpty()) {
                tags.add(t);
            }
        }
        return tags;
    }

    /** 在原文末尾追加 #标签（不重复列上已存在的）。 */
    public static String buildContentWithTags(String content, List<String> tags) {
        StringBuilder sb = new StringBuilder();
        for (String t : tags) {
            if (t.startsWith("#")) {
                sb.append(t).append(' ');
            } else {
                sb.append('#').append(t).append(' ');
            }
        }
        String block = sb.toString().trim();
        if (block.isEmpty()) {
            return content == null ? "" : content;
        }
        if (content == null || content.isBlank()) {
            return block;
        }
        return content + (content.endsWith("\n") ? "" : "\n\n") + block;
    }
}