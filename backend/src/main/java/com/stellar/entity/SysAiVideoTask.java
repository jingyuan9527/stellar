package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 视频生成异步任务：本地留痕，createTask 落库、getTask 轮询更新、完成存 sys_file。
 */
@Data
@TableName("sys_ai_video_task")
public class SysAiVideoTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long modelId;

    private Long providerId;

    /** account / ip */
    private String subjectType;

    private String subjectId;

    private String prompt;

    private String ratio;

    /** 时长(秒) */
    private Integer duration;

    private Integer width;

    private Integer height;

    private Integer numFrames;

    private Double frameRate;

    /** 供应商返回的 video_id */
    private String videoId;

    /** generating / completed / failed */
    private String status;

    private Long fileId;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
