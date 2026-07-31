package com.stellar.config;

import com.stellar.common.CacheConstants;
import com.stellar.ai.service.AiNotifyListener;
import com.stellar.ai.service.AiNotifyPublisher;
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
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
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
     * <p>value 序列化器必须同时满足两点（缺一即报错）：
     * <ul>
     *   <li>{@code .objectMapper(new ObjectMapper().registerModule(new JavaTimeModule()))} 注册 JavaTimeModule，
     *       否则序列化 {@link java.time.LocalDateTime} 报 "Java 8 date/time type not supported"；</li>
     *   <li>{@code .defaultTyping(true)} 启用 default typing（写入 {@code @class} 类型标记），
     *       否则反序列化时 POJO 退化为 {@link java.util.LinkedHashMap}，CGLIB 代理处强转抛 {@link ClassCastException}。
     *       注：builder 传入自定义 ObjectMapper 时，typing 默认是关闭的，必须显式开启。</li>
     * </ul>
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer valueSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(new ObjectMapper().registerModule(new JavaTimeModule()))
                .defaultTyping(true)
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
     * Redis pub/sub 监听容器：订阅 AI 任务通知 channel，收到消息交给 AiNotifyListener 处理。
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory, AiNotifyListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(listener, new ChannelTopic(AiNotifyPublisher.CHANNEL));
        return container;
    }
}
