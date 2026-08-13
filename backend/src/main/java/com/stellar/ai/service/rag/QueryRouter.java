package com.stellar.ai.service.rag;

/**
 * 查询复杂度路由器（loop 前置判定）。
 * <p>企业级 RAG 不是所有问题都走多轮迭代：简单/FAQ 问题一轮检索直接生成，
 * 复杂/模糊问题才进 loop（改写→检索→判定→再查）。路由把"要不要 loop"从管线里独立出来，
 * 避免简单问题也白烧多轮 LLM 调用。
 */
public interface QueryRouter {

    /**
     * 是否判定该查询需要多轮迭代检索。
     * 实现必须为纯启发式/低开销逻辑（不得调 LLM，否则路由器本身就成了成本大头）。
     */
    boolean needsLoop(String query);
}