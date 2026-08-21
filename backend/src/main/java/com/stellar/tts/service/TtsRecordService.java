package com.stellar.tts.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.tts.dto.TtsRecordQueryDTO;
import com.stellar.tts.dto.TtsRequest;
import com.stellar.tts.entity.TtsRecord;
import com.stellar.tts.port.TtsHistoryEntry;
import com.stellar.tts.port.TtsHistoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TTS 历史记录服务：合成结果落历史（经 {@link TtsHistoryStore} 缝，由 ai 侧落 ai_task 表）、
 * 分页查询、音频读取与删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsRecordService {

    private final TtsHistoryStore ttsHistoryStore;
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
        LocalDateTime now = LocalDateTime.now();
        ttsHistoryStore.record(new TtsHistoryEntry(null, text, audio,
                (long) audio.length, audioFormat,
                buildTtsExtra(voice, rate, pitch, volume),
                subjectType, subjectId, now, now));
    }

    public Page<TtsRecord> page(TtsRecordQueryDTO query) {
        Page<TtsHistoryEntry> result = ttsHistoryStore.page(query.getText(),
                query.getStartTime(), query.getEndTime(), query.getPageNum(), query.getPageSize());

        Page<TtsRecord> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(this::toTtsRecord).toList());
        return voPage;
    }

    public TtsRecord getAudio(Long id) {
        TtsHistoryEntry entry = ttsHistoryStore.findById(id);
        if (entry == null || entry.audioData() == null) {
            throw new BusinessException("音频数据不存在");
        }
        TtsRecord record = new TtsRecord();
        record.setAudioData(entry.audioData());
        record.setAudioFormat(entry.audioFormat());
        return record;
    }

    public void deleteById(Long id) {
        ttsHistoryStore.deleteById(id);
    }

    private TtsRecord toTtsRecord(TtsHistoryEntry entry) {
        TtsRecord record = new TtsRecord();
        record.setId(entry.id());
        record.setText(entry.text());
        record.setFileSize(entry.fileSize());
        record.setAudioFormat(entry.audioFormat());
        record.setOperator(entry.subjectId());
        record.setCreateTime(entry.createTime());
        if (entry.extra() != null) {
            try {
                var json = objectMapper.readTree(entry.extra());
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
