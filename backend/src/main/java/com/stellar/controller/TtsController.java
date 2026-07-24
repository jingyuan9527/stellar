package com.stellar.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
import com.stellar.dto.TtsRecordQueryDTO;
import com.stellar.dto.TtsRequest;
import com.stellar.entity.TtsRecord;
import com.stellar.enums.OperationType;
import com.stellar.service.TtsRecordService;
import com.stellar.service.TtsService;
import jakarta.validation.Valid;
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

    /**
     * Edge TTS 语音合成，返回 MP3 音频流，同时保存合成记录。
     * <p>对游客开放（edge 免费），受 IP 单日限流保护；游客合成记录 operator=anonymous。
     */
    @PublicAccess
    @RateLimit(daily = 20)
    @PostMapping("/edge/synthesize")
    @Log(title = "语音合成", type = OperationType.OTHER)
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody TtsRequest request) {
        byte[] audio = ttsService.synthesize(
                request.getText(),
                request.getVoice(),
                request.getRate(),
                request.getPitch(),
                request.getVolume()
        );

        // 保存合成记录（失败不影响合成结果）
        try {
            ttsRecordService.save(request, audio);
        } catch (Exception e) {
            log.warn("保存语音合成记录失败: {}", e.getMessage());
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
     * 按记录 ID 获取音频数据（公共墙，游客可试听/下载）。
     */
    @PublicAccess
    @GetMapping("/record/{id}/audio")
    public ResponseEntity<byte[]> recordAudio(@PathVariable Long id) {
        byte[] audio = ttsRecordService.getAudio(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=tts_" + id + ".mp3")
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
