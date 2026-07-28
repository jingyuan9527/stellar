package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.dto.TtsRecordQueryDTO;
import com.stellar.dto.TtsRequest;
import com.stellar.entity.SysUser;
import com.stellar.entity.TtsRecord;
import com.stellar.mapper.SysUserMapper;
import com.stellar.mapper.TtsRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 语音合成记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsRecordService {

    private final TtsRecordMapper ttsRecordMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 保存 Edge TTS 合成记录（audio_format=mp3）。
     */
    public void save(TtsRequest request, byte[] audio) {
        save(buildRecord(request.getText(), request.getVoice(),
                request.getRate(), request.getPitch(), request.getVolume(),
                audio, "mp3"));
    }

    /**
     * 保存 AI TTS 合成记录（audio_format=wav）。
     * <p>AI TTS 无 rate/pitch/volume 概念（风格由自然语言指令控制），均记默认值。
     */
    public void saveAiTts(String text, String voice, byte[] audio) {
        save(buildRecord(text, voice, 1.0, 1.0, 1.0, audio, "wav"));
    }

    private TtsRecord buildRecord(String text, String voice,
                                   Double rate, Double pitch, Double volume,
                                   byte[] audio, String audioFormat) {
        TtsRecord record = new TtsRecord();
        record.setText(text);
        record.setVoice(voice);
        record.setRate(rate);
        record.setPitch(pitch);
        record.setVolume(volume);
        record.setAudioData(audio);
        record.setFileSize((long) audio.length);
        record.setAudioFormat(audioFormat);
        record.setOperator(getCurrentUsername());
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    private void save(TtsRecord record) {
        ttsRecordMapper.insert(record);
    }

    /**
     * 分页查询合成记录（不加载音频数据）。
     */
    public Page<TtsRecord> page(TtsRecordQueryDTO query) {
        Page<TtsRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<TtsRecord> wrapper = new LambdaQueryWrapper<TtsRecord>()
                .like(StringUtils.hasText(query.getText()), TtsRecord::getText, query.getText())
                .eq(StringUtils.hasText(query.getVoice()), TtsRecord::getVoice, query.getVoice())
                .ge(query.getStartTime() != null, TtsRecord::getCreateTime, query.getStartTime())
                .le(query.getEndTime() != null, TtsRecord::getCreateTime, query.getEndTime())
                .orderByDesc(TtsRecord::getCreateTime);
        return ttsRecordMapper.selectPage(page, wrapper);
    }

    /**
     * 按主键获取音频数据及其格式（试听/下载时按格式设 Content-Type）。
     */
    public TtsRecord getAudio(Long id) {
        TtsRecord record = ttsRecordMapper.selectOne(
                new LambdaQueryWrapper<TtsRecord>()
                        .select(TtsRecord::getAudioData, TtsRecord::getAudioFormat)
                        .eq(TtsRecord::getId, id));
        if (record == null || record.getAudioData() == null) {
            throw new BusinessException("音频数据不存在");
        }
        return record;
    }

    /**
     * 逻辑删除合成记录。
     */
    public void deleteById(Long id) {
        ttsRecordMapper.deleteById(id);
    }

    private String getCurrentUsername() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            SysUser user = sysUserMapper.selectById(userId);
            return user != null ? user.getUsername() : "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
