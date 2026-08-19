package com.stellar.memos.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备忘同步状态记录视图（列表/最近一次用）。
 */
@Data
public class MemosSyncLogVO {

    private Long id;

    /** 触发方式：scheduled 定时 / manual 手动 */
    private String triggerType;

    /** 状态：success / partial / failed / skipped */
    private String status;

    private Integer fetched;

    private Integer created;

    private Integer updated;

    private Integer markedDeleted;

    private Integer errors;

    private Long durationMs;

    private String errorMessage;

    private LocalDateTime createTime;
}
