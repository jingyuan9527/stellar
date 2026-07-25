package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_ai_config")
public class SysAiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String endpoint;

    private String apiKey;

    private String model;

    /** 最近拉取到的可用模型列表，逗号分隔，重新拉取时覆盖 */
    private String availableModels;

    /** 神奇海螺 AI 匹配开关: 0关闭(纯随机) 1开启(AI语义匹配) */
    private Integer conchAiEnabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
