package com.stellar.infra;

/**
 * 缓存失效 Spring 事件：由 {@link CacheInvalidationListener} 从 Redis 广播转出，
 * 各业务服务（AiKnowledgeService / MemosRagService）以 @EventListener 订阅处理，
 * 避免 infra 包反向依赖业务包。
 */
public record CacheInvalidationEvent(String scope, String key) {
}