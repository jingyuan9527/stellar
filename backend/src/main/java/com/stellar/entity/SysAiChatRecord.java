package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 文本生成历史记录：流式生成结束自动落库，记录提示词/结果/请求与返回时间。
 * <p>主体按登录态区分（account/ip），便于游客按 IP 查看自己的历史。
 */
@Data
@TableName("sys_ai_chat_record")
public class SysAiChatRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** account / ip */
    private String subjectType;

    private String subjectId;

    private Long providerId;

    private String model;

    /** 实际发送给 LLM 的完整 prompt */
    private String prompt;

    /** 流式完整文本 */
    private String result;

    /** success / failed */
    private String status;

    private String errorMsg;

    private LocalDateTime requestTime;

    private LocalDateTime responseTime;

    private Long durationMs;

    private LocalDateTime createTime;
}
