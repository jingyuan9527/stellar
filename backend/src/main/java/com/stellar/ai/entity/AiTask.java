package com.stellar.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_task")
public class AiTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskType;

    private String subjectType;

    private String subjectId;

    private Long providerId;

    private String model;

    private String prompt;

    private String result;

    private String status;

    private String errorMsg;

    private Long fileId;

    @TableField(select = false)
    private byte[] fileData;

    private Long fileSize;

    private String audioFormat;

    private String extra;

    private LocalDateTime requestTime;

    private LocalDateTime responseTime;

    private Long durationMs;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
