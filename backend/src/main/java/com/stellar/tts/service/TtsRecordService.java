package com.stellar.tts.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.common.BusinessException;
import com.stellar.tts.dto.TtsRecordQueryDTO;
import com.stellar.tts.dto.TtsRequest;
import com.stellar.tts.entity.TtsRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TtsRecordService {

    private final AiTaskMapper aiTaskMapper;
    private final ObjectMapper objectMapper;

    public void save(TtsRequest request, byte[] audio, String subjectType, String subjectId) {
        saveRecord(request.getText(), request.getVoice(),
                request.getRate(), request.getPitch(), request.getVolume(),
                audio, "mp3", subjectType, subjectId);
    }

    public void saveAiTts(String text, String voice, byte[] audio, String subjectType, String subjectId) {
        saveRecord(text, voice, 1.0, 1.0, 1.0, audio, "wav", subjectType, subjectId);
    }

    public void saveChatTts(String text, String voice, byte[] audio, String audioFormat,
                            String subjectType, String subjectId) {
        saveRecord(text, voice, 1.0, 1.0, 1.0, audio, audioFormat, subjectType, subjectId);
    }

    private void saveRecord(String text, String voice,
                             Double rate, Double pitch, Double volume,
                             byte[] audio, String audioFormat, String subjectType, String subjectId) {
        AiTask task = new AiTask();
        task.setTaskType("tts");
        task.setSubjectType(subjectType);
        task.setSubjectId(subjectId);
        task.setPrompt(text);
        task.setStatus("success");
        task.setFileData(audio);
        task.setFileSize((long) audio.length);
        task.setAudioFormat(audioFormat);
        task.setExtra(buildTtsExtra(voice, rate, pitch, volume));
        task.setRequestTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        aiTaskMapper.insert(task);
    }

    public Page<TtsRecord> page(TtsRecordQueryDTO query) {
        Page<AiTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getTaskType, "tts")
                .like(StringUtils.hasText(query.getText()), AiTask::getPrompt, query.getText())
                .ge(query.getStartTime() != null, AiTask::getCreateTime, query.getStartTime())
                .le(query.getEndTime() != null, AiTask::getCreateTime, query.getEndTime())
                .orderByDesc(AiTask::getCreateTime);
        Page<AiTask> result = aiTaskMapper.selectPage(page, wrapper);

        Page<TtsRecord> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(this::toTtsRecord).toList());
        return voPage;
    }

    public TtsRecord getAudio(Long id) {
        AiTask task = aiTaskMapper.selectById(id);
        if (task == null || task.getFileData() == null) {
            throw new BusinessException("音频数据不存在");
        }
        TtsRecord record = new TtsRecord();
        record.setAudioData(task.getFileData());
        record.setAudioFormat(task.getAudioFormat());
        return record;
    }

    public void deleteById(Long id) {
        aiTaskMapper.deleteById(id);
    }

    private TtsRecord toTtsRecord(AiTask task) {
        TtsRecord record = new TtsRecord();
        record.setId(task.getId());
        record.setText(task.getPrompt());
        record.setFileSize(task.getFileSize());
        record.setAudioFormat(task.getAudioFormat());
        record.setOperator(task.getSubjectId());
        record.setCreateTime(task.getCreateTime());
        if (task.getExtra() != null) {
            try {
                var json = objectMapper.readTree(task.getExtra());
                record.setVoice(json.has("voice") ? json.get("voice").asText() : "");
            } catch (Exception ignored) {}
        }
        return record;
    }

    private String buildTtsExtra(String voice, Double rate, Double pitch, Double volume) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("voice", voice);
        extra.put("rate", rate);
        extra.put("pitch", pitch);
        extra.put("volume", volume);
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (Exception e) {
            log.warn("[TTS] extra JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

}
