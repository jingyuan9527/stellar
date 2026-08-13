package com.stellar.ai.service.rag;

/**
 * 资料充分性判定结果。
 *
 * @param sufficient true=资料足够可直接生成；false=存在缺口需下一轮补查
 * @param gap        缺口描述（sufficient=false 时有效，喂给下一轮查询改写器补足方向）
 */
public record Judgement(boolean sufficient, String gap) {

    /** 解析失败/异常时的保守放行值（宁可多花钱生成，不可死循环） */
    public static Judgement ok() {
        return new Judgement(true, null);
    }
}