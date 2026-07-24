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
     * 保存合成记录。
     */
    public void save(TtsRequest request, byte[] audio) {
        TtsRecord record = new TtsRecord();
        record.setText(request.getText());
        record.setVoice(request.getVoice());
        record.setRate(request.getRate());
        record.setPitch(request.getPitch());
        record.setVolume(request.getVolume());
        record.setAudioData(audio);
        record.setFileSize((long) audio.length);
        record.setOperator(getCurrentUsername());
        record.setCreateTime(LocalDateTime.now());
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
     * 按主键获取音频数据。
     */
    public byte[] getAudio(Long id) {
        TtsRecord record = ttsRecordMapper.selectOne(
                new LambdaQueryWrapper<TtsRecord>()
                        .select(TtsRecord::getAudioData)
                        .eq(TtsRecord::getId, id));
        if (record == null || record.getAudioData() == null) {
            throw new BusinessException("音频数据不存在");
        }
        return record.getAudioData();
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
