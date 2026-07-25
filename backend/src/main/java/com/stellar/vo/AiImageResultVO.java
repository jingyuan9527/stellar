package com.stellar.vo;

import lombok.Data;

/**
 * AI 图片生成结果。
 */
@Data
public class AiImageResultVO {

    private Long fileId;

    /** 图片读取地址（GET /file/{id}，游客可读） */
    private String url;
}
