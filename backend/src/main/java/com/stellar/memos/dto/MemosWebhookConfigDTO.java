package com.stellar.memos.dto;

import lombok.Data;

/**
 * Webhook 配置保存：签名密钥（Memos 创建 webhook 时生成的 whsec_ 形式）。
 */
@Data
public class MemosWebhookConfigDTO {

    /** 签名密钥，为空不修改 */
    private String secret;
}