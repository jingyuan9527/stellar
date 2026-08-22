package com.stellar.memos.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备忘同步笔记展示（列表/详情）。
 */
@Data
public class MemosNoteVO {

    private Long id;

    /** Memos 笔记 UID */
    private String uid;

    /** 笔记原文（Markdown） */
    private String content;

    /** 标签列表 */
    private List<String> tags;

    /** 标签是否已写回远端 */
    private Integer tagsSynced;

    /** 远端是否已删除：0 存活 1 标记删除 */
    private Integer remoteDeleted;

    /** 正文是否本地已编辑未同步：0 无 1 有 */
    private Integer localEdited;

    /** 是否与远端冲突待裁决：0 否 1 是 */
    private Integer conflict;

    private LocalDateTime remoteCreateTime;

    private LocalDateTime remoteUpdateTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
