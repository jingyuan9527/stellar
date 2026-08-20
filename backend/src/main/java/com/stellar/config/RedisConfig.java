package com.stellar.config;

import com.stellar.common.CacheConstants;
import com.stellar.ai.service.AiNotifyListener;
import com.stellar.ai.service.AiNotifyPublisher;
import com.stellar.infra.CacheInvalidationListener;
import com.stellar.infra.CacheInvalidationPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 配置：统一 key 序列化为 String、value 为 JSON（可读且跨语言）。
 * Spring Cache 的 cacheName 统一加 {@code stellar:} 前缀，与其他项目隔离。
 * <p>限流等纯计数场景用 Spring Boot 自动配置的 {@link org.springframework.data.redis.core.StringRedisTemplate}。
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 业务 RedisTemplate：String key + JSON value，供直接操作 Redis 使用。
     * <p>value 序列化器与 {@link #cacheManager} 一致<b>关闭 default typing</b>（S2 安全收敛）：
     * 不写 {@code @class} 类型标记。pub/sub 通道改由各 Listener 用类型化
     * {@code Jackson2JsonRedisSerializer(消息类.class)} 直读（见 CacheInvalidationListener/AiNotifyListener），
     * 本模板的 payload 为纯 JSON 即可兼容，无需依赖多态类型信息。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(new ObjectMapper().registerModule(new JavaTimeModule()))
                .build();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Spring Cache 管理器：cacheName 加 {@code stellar:} 前缀，默认 30min TTL，
     * 禁止缓存 null（防穿透由调用方自行处理）。
     * <p>value 序列化器<b>关闭 default typing</b>（S2 安全收敛）：不写 {@code @class} 类型标记，
     * 杜绝 Redis 被攻陷时经不可信 {@code @class} 触发反序列化 gadget 的风险。
     * 代价是缓存的 POJO 读回时退化为 {@link java.util.LinkedHashMap}——当前缓存落点均为
     * 可信配置型数据且调用方只透传序列化或判空，不依赖强类型，可接受。
     * 注意：builder 传自定义 ObjectMapper 时 typing 默认即关闭，显式传入 {code ObjectMapper}
     * 仅为注册 JavaTimeModule 支持 {@link java.time.LocalDateTime} 序列化。
     * <p>旧版本写入的带 {@code @class} 缓存条目无法按原形状读回，由 {@link RedisCacheBootstrap}
     * 启动时统一清理迁移（详见该类）。
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer valueSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(new ObjectMapper().registerModule(new JavaTimeModule()))
                .build();
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .prefixCacheNameWith(CacheConstants.KEY_PREFIX)
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer))
                .disableCachingNullValues();
        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * Redis pub/sub 监听容器：订阅 AI 任务通知 + RAG 缓存失效两个 channel，
     * 分别交给对应 Listener 处理。
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory, AiNotifyListener aiNotifyListener,
            CacheInvalidationListener cacheInvalidationListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(aiNotifyListener, new ChannelTopic(AiNotifyPublisher.CHANNEL));
        container.addMessageListener(cacheInvalidationListener,
                new ChannelTopic(CacheInvalidationPublisher.CHANNEL));
        return container;
    }
}
