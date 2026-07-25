package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统设置：全局开关/单值配置（如 conch_ai_enabled）。
 */
@Data
@TableName("sys_setting")
public class SysSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设置键，唯一，如 conch_ai_enabled */
    private String settingKey;

    private String settingValue;

    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
