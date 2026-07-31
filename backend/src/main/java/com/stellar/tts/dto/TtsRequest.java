package com.stellar.tts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 语音合成请求参数
 */
@Data
public class TtsRequest {

    /**
     * 合成文本
     */
    @NotBlank(message = "合成文本不能为空")
    @Size(max = 2000, message = "文本长度不能超过2000字")
    private String text;

    /**
     * 发音人名称，如 zh-CN-XiaoxiaoNeural
     */
    @NotBlank(message = "发音人不能为空")
    private String voice;

    /**
     * 语速，0.5 ~ 2.0，默认 1.0
     */
    private Double rate = 1.0;

    /**
     * 音调，0 ~ 2.0，默认 1.0
     */
    private Double pitch = 1.0;

    /**
     * 音量，0 ~ 1.0，默认 1.0
     */
    private Double volume = 1.0;
}
