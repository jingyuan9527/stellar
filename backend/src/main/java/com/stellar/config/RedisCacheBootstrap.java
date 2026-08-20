package com.stellar.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时清理全部 Spring Cache（缓存均为可随时重建的派生数据，清空无害）。
 * <p>原因：S2 关闭了 value 序列化器的 default typing，旧版本写入的带 {@code @class}
 * 类型标记条目无法按原形状反序列化（List 会退化成带 {@code list} 包裹的 Map）。
 * 启动统一清一次让缓存按新格式重建，避免部署后 30min TTL 窗口内前端读到错误形状。
 * <p>Redis 暂不可用时逐缓存 try/catch 降级（缓存读侧本就需要 Redis，不影响启动主流程）。
 * cacheName 清单与各 {@code @Cacheable} 落点保持一致，新增缓存时需同步补入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheBootstrap implements CommandLineRunner {

    /** 与各 Service {@code @Cacheable(cacheNames=...)} 的 cacheName 一一对应 */
    private static final List<String> CACHE_NAMES = List.of(
            "ai-model", "ai-provider", "ai-persona", "setting", "setting-bool",
            "dict", "profile", "profile-project", "menu-visibility");

    private final CacheManager cacheManager;

    @Override
    public void run(String... args) {
        for (String name : CACHE_NAMES) {
            try {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                    log.info("[Redis缓存] 启动清理完成 cache={}", name);
                }
            } catch (Exception e) {
                log.warn("[Redis缓存] 启动清理失败（Redis 暂不可用时忽略，缓存按 TTL 自然过期） cache={} err={}",
                        name, e.getMessage());
            }
        }
    }
}