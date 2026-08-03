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

    private LocalDateTime remoteCreateTime;

    private LocalDateTime remoteUpdateTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
