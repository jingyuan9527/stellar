package com.stellar.infra;

import java.io.Serializable;

/**
 * RAG 检索缓存失效消息（Redis pub/sub 广播载荷）。
 * scope 区分缓存主体：kb=知识库（key=kbId）、memos=备忘笔记（key 恒为 null，全量失效）。
 */
public record CacheInvalidationMessage(String scope, String key) implements Serializable {

    public static final String SCOPE_KB = "kb";
    public static final String SCOPE_MEMOS = "memos";
}