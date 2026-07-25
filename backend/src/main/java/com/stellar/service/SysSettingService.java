package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.entity.SysSetting;
import com.stellar.mapper.SysSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 系统设置服务：全局开关/单值配置的读写。
 * <p>用于 conch_ai_enabled 等不归属具体业务实体的全局开关。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSettingService {

    private final SysSettingMapper settingMapper;

    /**
     * 读取设置值，不存在或为空返回默认值。
     */
    public String get(String key, String defaultValue) {
        if (!StringUtils.hasText(key)) {
            return defaultValue;
        }
        SysSetting s = settingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, key));
        if (s == null || s.getSettingValue() == null) {
            return defaultValue;
        }
        return s.getSettingValue();
    }

    /**
     * 读取布尔型设置：值为 "1" 或 "true" 视为 true。
     */
    public boolean getAsBoolean(String key, boolean defaultValue) {
        String v = get(key, null);
        if (v == null) {
            return defaultValue;
        }
        String t = v.trim();
        return "1".equals(t) || "true".equalsIgnoreCase(t);
    }

    /**
     * 写入设置（upsert：存在则更新，不存在则插入）。
     */
    public void set(String key, String value, String description) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        SysSetting exist = settingMapper.selectOne(new LambdaQueryWrapper<SysSetting>()
                .eq(SysSetting::getSettingKey, key));
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            SysSetting s = new SysSetting();
            s.setSettingKey(key);
            s.setSettingValue(value);
            s.setDescription(description);
            s.setCreateTime(now);
            s.setUpdateTime(now);
            settingMapper.insert(s);
            log.info("[系统设置] 新增 key={} value={}", key, value);
        } else {
            exist.setSettingValue(value);
            if (StringUtils.hasText(description)) {
                exist.setDescription(description);
            }
            exist.setUpdateTime(now);
            settingMapper.updateById(exist);
            log.info("[系统设置] 更新 key={} value={}", key, value);
        }
    }
}
