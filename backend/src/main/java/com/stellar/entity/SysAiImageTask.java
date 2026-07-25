package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 图片生成异步任务：请求立即落库，异步线程生成后更新 file_id。
 */
@Data
@TableName("sys_ai_image_task")
public class SysAiImageTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    private Long providerId;

    /** account / ip */
    private String subjectType;

    private String subjectId;

    private String prompt;

    private String size;

    private String ratio;

    /** generating / completed / failed */
    private String status;

    private Long fileId;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
