package com.stellar.ai.service;

/**
 * 向量工具：embedding 列 JSON 数组文本 {@code [v1,v2,...]} 与 float[] 互转、余弦相似度。
 * <p>无 pgvector 依赖，向量以 JSON 文本存 PG，纯 Java 内存余弦检索。
 * 供 AI 知识库（{@link AiKnowledgeService}）与备忘笔记 RAG（MemosRagService）等检索场景复用的公共静态工具。
 */
public final class VectorOps {

    private VectorOps() {
    }

    /** 向量转 JSON 数组文本 '[v1,v2,...]'，用于 SQL 绑定。 */
    public static String toVectorLiteral(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * 解析 embedding 文本 '[1.0,2.0,...]' 为 float[]。非法返回 null。
     */
    public static float[] parseVector(String text) {
        if (text == null || text.isBlank()) return null;
        String s = text.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        if (s.isBlank()) return null;
        String[] parts = s.split(",");
        float[] vec = new float[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                vec[i] = Float.parseFloat(parts[i].trim());
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return vec;
    }

    /** 余弦相似度。维度不一致或任一为零向量返回 0。 */
    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
