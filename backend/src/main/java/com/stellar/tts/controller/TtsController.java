package com.stellar.tts.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
import com.stellar.tts.dto.AiTtsRequest;
import com.stellar.tts.dto.TtsRecordQueryDTO;
import com.stellar.tts.dto.TtsRequest;
import com.stellar.tts.entity.TtsRecord;
import com.stellar.enums.OperationType;
import com.stellar.tts.service.AiTtsService;
import com.stellar.tts.service.TtsRecordService;
import com.stellar.tts.service.TtsService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.stellar.infra.SubjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 语音合成接口
 */
@Slf4j
@RestController
@RequestMapping("/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TtsService ttsService;
    private final TtsRecordService ttsRecordService;
    private final AiTtsService aiTtsService;

    /**
     * Edge TTS 语音合成，返回 MP3 音频流，同时保存合成记录。
     * <p>对游客开放（edge 免费），受 IP 单日限流保护；记录主体按登录账号或游客 IP 保存。
     */
    @PublicAccess
    @RateLimit(daily = 20)
    @PostMapping("/edge/synthesize")
    @Log(title = "语音合成", type = OperationType.OTHER)
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody TtsRequest request,
                                             HttpServletRequest servletRequest) {
        byte[] audio = ttsService.synthesize(
                request.getText(),
                request.getVoice(),
                request.getRate(),
                request.getPitch(),
                request.getVolume()
        );

        // 保存合成记录（失败不影响合成结果）
        try {
            ttsRecordService.save(request, audio, SubjectUtils.subjectType(),
                    SubjectUtils.subjectId(servletRequest));
        } catch (Exception e) {
            log.warn("保存语音合成记录失败: {}", e.getMessage(), e);
        }

        String fileName = URLEncoder.encode("语音合成", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=" + fileName + ".mp3")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.length))
                .body(audio);
    }

    /**
     * 分页查询合成历史（公共墙，游客可读）。
     */
    @PublicAccess
    @GetMapping("/record/page")
    @Log(title = "合成历史", type = OperationType.QUERY)
    public Result<Page<TtsRecord>> recordPage(@ModelAttribute TtsRecordQueryDTO query) {
        return Result.success(ttsRecordService.page(query));
    }

    /**
     * AI 语音合成（MiMo-V2.5-TTS），返回 WAV 音频流，同时保存合成记录。
     * <p>需登录（不加 @PublicAccess），AUDIO 类型模型按 token 计费。
     */
    @PostMapping("/ai/synthesize")
    @Log(title = "AI语音合成", type = OperationType.OTHER)
    public ResponseEntity<byte[]> aiSynthesize(@Valid @RequestBody AiTtsRequest request,
                                               HttpServletRequest servletRequest) {
        byte[] audio = aiTtsService.synthesize(
                request.getModelId(),
                request.getText(),
                request.getVoice(),
                request.getStyle()
        );

        // 保存合成记录（失败不影响合成结果）
        try {
            ttsRecordService.saveAiTts(request.getText(), request.getVoice(), audio,
                    SubjectUtils.subjectType(), SubjectUtils.subjectId(servletRequest));
        } catch (Exception e) {
            log.warn("保存 AI 语音合成记录失败: {}", e.getMessage(), e);
        }

        String fileName = URLEncoder.encode("AI语音合成", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=" + fileName + ".wav")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.length))
                .body(audio);
    }

    /**
     * 按记录 ID 获取音频数据（公共墙，游客可试听/下载）。
     * <p>按记录的 audio_format 设 Content-Type（Edge=mp3，AI=wav）。
     */
    @PublicAccess
    @GetMapping("/record/{id}/audio")
    public ResponseEntity<byte[]> recordAudio(@PathVariable Long id) {
        TtsRecord record = ttsRecordService.getAudio(id);
        String format = record.getAudioFormat() != null ? record.getAudioFormat() : "mp3";
        boolean isWav = "wav".equals(format);
        String contentType = isWav ? "audio/wav" : "audio/mpeg";
        String ext = isWav ? "wav" : "mp3";
        byte[] audio = record.getAudioData();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=tts_" + id + "." + ext)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.length))
                .body(audio);
    }

    /**
     * 删除合成记录。
     */
    @DeleteMapping("/record/{id}")
    @Log(title = "合成历史", type = OperationType.DELETE)
    public Result<Void> deleteRecord(@PathVariable Long id) {
        ttsRecordService.deleteById(id);
        return Result.success();
    }
}
