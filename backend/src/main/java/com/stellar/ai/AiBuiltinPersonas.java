package com.stellar.ai;

import java.util.List;

/**
 * 内置人设种子：用于恢复默认与幂等播种。与 schema.sql 内置种子保持一致。
 */
public final class AiBuiltinPersonas {

    public record Seed(String name, String systemPrompt, String description, int sortOrder) {}

    public static final List<Seed> SEEDS = List.of(
            new Seed("通用助手",
                    "你是一个友好、博学的助手。请用简洁清晰的中文回答用户问题。",
                    "默认通用对话助手", 0),
            new Seed("程序员",
                    "你是一位资深全栈工程师，精通 Java、Vue、TypeScript、数据库与系统设计。回答技术问题时给出准确、可落地的方案与代码示例，指出潜在坑点。",
                    "技术问答助手", 1),
            new Seed("写作助手",
                    "你是一位优秀的写作搭档，擅长润色、改写、构思大纲与生成多平台文案。根据用户需求提供多种风格的文本，并简要说明取舍。",
                    "写作与文案助手", 2)
    );

    public static Seed findByName(String name) {
        return SEEDS.stream().filter(s -> s.name.equals(name)).findFirst().orElse(null);
    }

    private AiBuiltinPersonas() {}
}
