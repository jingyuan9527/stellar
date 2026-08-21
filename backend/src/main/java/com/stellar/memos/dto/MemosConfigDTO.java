package com.stellar.memos.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 备忘同步配置（Memos 域名 / Token / AI 打标签提示词模板）。
 * <p>Token 为空时保留原值（与供应商 apiKey 惯例一致，避免回显）。
 */
@Data
public class MemosConfigDTO {

    /** Memos 实例域名（末尾不带 /） */
    @Size(max = 300, message = "域名过长")
    private String baseUrl;

    /** Memos API Token（为空保留原值） */
    @Size(max = 500, message = "Token 过长")
    private String token;

    /** AI 打标签提示词模板（含 {{content}} 占位符） */
    @Size(max = 10000, message = "提示词模板过长")
    private String promptTemplate;
}
