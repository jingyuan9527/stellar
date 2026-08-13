package com.stellar.ai.service.rag;

/**
 * 资料充分性判定器（loop 核心：判"资料够不够"）。
 * <p>每轮检索+重排后由 Judger 判断当前 top-k 资料是否足以回答用户问题：
 * 不够则把缺口（gap）反馈给下一轮查询改写器补查；够了或达轮数上限才进入生成。
 */
public interface Judger {

    /**
     * 判定给定资料是否足以回答查询。
     * 实现必须保证：任何异常/解析失败都返回 {@link Judgement#sufficient()}=true（保守放行，防死循环）。
     *
     * @param query   原始用户问题（不用改写后的，判定对象是"能不能答原始问题"）
     * @param hits    当前轮检索+重排后的 top 命中（已排序、截断到注入规模）
     * @param modelId TEXT 模型 id（可空，用 TEXT 默认模型）
     * @return 判定结果；失败返回 sufficient=true
     */
    Judgement judge(String query, java.util.List<RagHit> hits, Long modelId);
}