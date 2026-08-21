package com.stellar.infra;

import com.stellar.common.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Standard Webhooks 兼容的 HMAC Webhook 验签（纯密码学校验，无业务依赖）：
 * 头完整性 → 时间戳格式与容差窗口（防重放）→ HMAC-SHA256 常量时间比较。
 * 密钥支持 whsec_ 前缀 base64 解码（{@link #decodeSecret}）。
 */
@Component
public class HmacWebhookVerifier {

    /**
     * 全量校验，任一环节失败抛 {@link BusinessException}（由上层转 4xx 拒绝投递）。
     *
     * @param secretKey        已解析的签名密钥字节（见 {@link #decodeSecret}）
     * @param toleranceSeconds 签名时间戳容差（秒）
     */
    public void verify(byte[] rawBody, String webhookId, String timestamp, String signature,
                       byte[] secretKey, long toleranceSeconds) {
        if (!StringUtils.hasText(webhookId) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            throw new BusinessException("Webhook 缺少签名头（webhook-id/webhook-timestamp/webhook-signature）");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BusinessException("Webhook 时间戳格式非法");
        }
        long nowSec = System.currentTimeMillis() / 1000;
        if (Math.abs(nowSec - ts) > toleranceSeconds) {
            throw new BusinessException("Webhook 时间戳超出容差窗口");
        }
        String signedContent = webhookId + "." + timestamp + "." + new String(rawBody, StandardCharsets.UTF_8);
        String expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            expected = "v1," + Base64.getEncoder().encodeToString(
                    mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("Webhook 签名计算失败: " + e.getMessage());
        }
        if (!MessageDigest.isEqual(signature.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("Webhook 签名校验失败");
        }
    }

    /** 解析签名密钥：whsec_ 前缀 base64 解码，其余按 UTF-8 原样（与 Memos 端 resolveSigningKey 一致）。 */
    public byte[] decodeSecret(String secret) {
        if (secret.startsWith("whsec_")) {
            try {
                return Base64.getDecoder().decode(secret.substring("whsec_".length()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Webhook 签名密钥格式错误（whsec_ 后非合法 base64）");
            }
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}