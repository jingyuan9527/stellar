package com.stellar.memos.vo;

import lombok.Data;

/**
 * Webhook 配置展示：签名密钥不回显原文，仅返回是否已配置。
 */
@Data
public class MemosWebhookConfigVO {

    /** 是否已配置签名密钥（whsec_ 开头） */
    private Boolean secretConfigured;
}