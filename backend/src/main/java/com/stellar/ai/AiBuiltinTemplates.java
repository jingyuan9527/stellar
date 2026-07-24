package com.stellar.ai;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 内置提示词模板种子数据，供 DataInitializer 初始化和 AiTemplateService 恢复默认使用。
 */
public class AiBuiltinTemplates {

    public record Seed(String key, String name, String platform, String prompt) {}

    public static final List<Seed> SEEDS = List.of(
            new Seed("bilibili", "B站通用", "bilibili",
                    "你是B站视频文案专家。根据主题「{{topic}}」，生成5条适合B站的视频标题（偏干货/科普/盘点，可带【】或数字，20字以内），"
                            + "一段100字以内简介（说明视频价值），8个相关话题标签（不带#号，适合B站分区）。\n"
                            + "只返回JSON:{\"titles\":[\"...\"],\"description\":\"...\",\"tags\":[\"...\"]}"),
            new Seed("douyin", "抖音爆款", "douyin",
                    "你是抖音短视频文案专家。根据主题「{{topic}}」，生成5条适合抖音的爆款标题（短促有力、情绪强、带钩子，15字以内），"
                            + "一段80字以内简介（口语化、有悬念），8个相关话题标签（不带#号）。\n"
                            + "只返回JSON:{\"titles\":[\"...\"],\"description\":\"...\",\"tags\":[\"...\"]}"),
            new Seed("xiaohongshu", "小红书种草", "xiaohongshu",
                    "你是小红书笔记文案专家。根据主题「{{topic}}」，生成5条适合小红书的标题（带emoji、生活化、种草感，20字以内），"
                            + "一段120字以内简介（口语、有\"姐妹们\"等亲切感），8个相关话题标签（不带#号，小红书风格）。\n"
                            + "只返回JSON:{\"titles\":[\"...\"],\"description\":\"...\",\"tags\":[\"...\"]}")
    );

    private static final Map<String, Seed> BY_KEY = SEEDS.stream()
            .collect(Collectors.toMap(Seed::key, s -> s));

    /**
     * 通过种子 key（等于内置模板的 platform）查找原始种子数据。
     */
    public static Seed findByKey(String key) {
        return BY_KEY.get(key);
    }
}
