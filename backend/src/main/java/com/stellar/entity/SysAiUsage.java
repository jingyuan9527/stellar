package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI token 消费记录：每次 LLM 调用记录消耗，用于统计展示与计费。
 */
@Data
@TableName("sys_ai_usage")
public class SysAiUsage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主体类型: account / ip */
    private String subjectType;

    /** 主体 ID: userId 或 IP */
    private String subjectId;

    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** token 来源: usage(LLM 返回) / estimate(字符估算) */
    private String source;

    private LocalDateTime createTime;
}
