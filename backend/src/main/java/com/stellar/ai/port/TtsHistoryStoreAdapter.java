package com.stellar.ai.port;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.service.AiTaskService;
import com.stellar.tts.port.TtsHistoryEntry;
import com.stellar.tts.port.TtsHistoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * {@link TtsHistoryStore} 的 ai 实现：TTS 历史落 ai_task 表（task_type=tts），
 * 负责 TtsHistoryEntry ↔ AiTask 映射，让 tts 模块无需编译期依赖 ai。
 */
@Component
@RequiredArgsConstructor
public class TtsHistoryStoreAdapter implements TtsHistoryStore {

    private final AiTaskService aiTaskService;

    @Override
    public void record(TtsHistoryEntry entry) {
        AiTask task = new AiTask();
        task.setTaskType("tts");
        task.setStatus("success");
        task.setSubjectType(entry.subjectType());
        task.setSubjectId(entry.subjectId());
        task.setPrompt(entry.text());
        task.setFileData(entry.audioData());
        task.setFileSize(entry.fileSize());
        task.setAudioFormat(entry.audioFormat());
        task.setExtra(entry.extra());
        task.setRequestTime(entry.requestTime() != null ? entry.requestTime() : LocalDateTime.now());
        task.setCreateTime(entry.createTime() != null ? entry.createTime() : LocalDateTime.now());
        aiTaskService.record(task);
    }

    @Override
    public Page<TtsHistoryEntry> page(String textLike, LocalDateTime startTime, LocalDateTime endTime,
                                      int pageNum, int pageSize) {
        Page<AiTask> result = aiTaskService.pageByType("tts", textLike, startTime, endTime, pageNum, pageSize);
        Page<TtsHistoryEntry> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setPages(result.getPages());
        page.setRecords(result.getRecords().stream().map(this::toEntry).toList());
        return page;
    }

    @Override
    public TtsHistoryEntry findById(Long id) {
        AiTask task = aiTaskService.getById(id);
        return task == null ? null : toEntry(task);
    }

    @Override
    public void deleteById(Long id) {
        aiTaskService.deleteById(id);
    }

    private TtsHistoryEntry toEntry(AiTask task) {
        return new TtsHistoryEntry(task.getId(), task.getPrompt(), task.getFileData(),
                task.getFileSize(), task.getAudioFormat(), task.getExtra(),
                task.getSubjectType(), task.getSubjectId(),
                task.getRequestTime(), task.getCreateTime());
    }
}
