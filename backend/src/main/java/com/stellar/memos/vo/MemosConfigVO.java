package com.stellar.memos.vo;

import lombok.Data;

/**
 * 备忘同步配置展示。Token 不回显原文，仅标记是否已配置。
 */
@Data
public class MemosConfigVO {

    /** Memos 实例域名 */
    private String baseUrl;

    /** 是否已配置 Token */
    private Boolean tokenConfigured;

    /** AI 打标签提示词模板 */
    private String promptTemplate;
}
