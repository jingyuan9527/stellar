package com.stellar.game.vo;

import lombok.Data;

/**
 * 神奇海螺提问结果（返回给前端：文本 + 音频地址）
 */
@Data
public class ConchAskResultVO {

    /** 命中的预设回答 ID */
    private Long answerId;

    /** 回答文本 */
    private String answerText;

    /** 音频地址（GET /tts/conch/answer/{id}/audio） */
    private String audioUrl;
}
