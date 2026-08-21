package com.stellar.memos.service;

import com.stellar.common.BusinessException;
import com.stellar.infra.HmacWebhookVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Memos webhook 防线：签名校验（密钥读取 + 委托 {@link HmacWebhookVerifier}）与
 * webhook-id 去重（Redis SETNX + TTL，防重放）。
 * <p>从 MemosService 抽出，密码学与 Redis 细节不留在同步编排里。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemosWebhookGuard {

    /** webhook-id 去重 key 前缀（Redis），TTL 内重复投递直接忽略 */
    private static final String WEBHOOK_DEDUP_KEY_PREFIX = "stellar:memos:webhook:";
    private static final Duration WEBHOOK_DEDUP_TTL = Duration.ofMinutes(5);

    /** 签名时间戳容差（秒）：超出视为失效，防重放 */
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300;

    private final RedisTemplate<String, Object> redisTemplate;
    private final com.stellar.system.service.SysSettingService sysSettingService;
    private final HmacWebhookVerifier verifier;

    /** 校验签名（Standard Webhooks 兼容）：时效 + HMAC-SHA256 常量时间比较。失败抛 BusinessException。 */
    public void verifySignature(byte[] rawBody, String webhookId, String timestamp, String signature) {
        String secret = sysSettingService.get(MemosService.KEY_WEBHOOK_SECRET, "");
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("Webhook 签名密钥未配置");
        }
        verifier.verify(rawBody, webhookId, timestamp, signature,
                verifier.decodeSecret(secret), SIGNATURE_TOLERANCE_SECONDS);
    }

    /** webhook-id 去重：Redis SETNX + TTL，返回 false 表示已处理过（防重放）。 */
    public boolean dedupeWebhookId(String webhookId) {
        try {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(
                    WEBHOOK_DEDUP_KEY_PREFIX + webhookId, "1", WEBHOOK_DEDUP_TTL);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            log.warn("[备忘同步] Webhook 去重 Redis 异常，放行处理 id={}: {}", webhookId, e.getMessage());
            return true;
        }
    }
}