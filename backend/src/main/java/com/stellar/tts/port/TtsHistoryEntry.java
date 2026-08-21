package com.stellar.tts.port;

import java.time.LocalDateTime;

/**
 * 一条 TTS 历史记录（tts 模块中立视图），由 ai 侧 TTS 历史存储实现落取。
 */
public record TtsHistoryEntry(
        Long id,
        String text,
        byte[] audioData,
        Long fileSize,
        String audioFormat,
        /** JSON 扩展信息（voice/rate/pitch/volume） */
        String extra,
        String subjectType,
        String subjectId,
        LocalDateTime requestTime,
        LocalDateTime createTime
) {
}
