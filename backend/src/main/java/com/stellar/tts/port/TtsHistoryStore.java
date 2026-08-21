package com.stellar.tts.port;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;

/**
 * TTS 历史存储缝：tts 只依赖本接口读写历史，不直接依赖 ai 模块；
 * 由 ai 模块提供实现（落 ai_task 表 task_type=tts）。
 */
public interface TtsHistoryStore {

    void record(TtsHistoryEntry entry);

    /** 文本模糊 + 时间范围过滤，创建时间倒序。 */
    Page<TtsHistoryEntry> page(String textLike, LocalDateTime startTime, LocalDateTime endTime,
                               int pageNum, int pageSize);

    TtsHistoryEntry findById(Long id);

    void deleteById(Long id);
}
