package com.stellar.memos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备忘同步笔记备份（全量拉取 memo.booksy.cf 笔记到本地做备份）。
 * <p>远端删除后本地不删数据，仅标记 {@code remote_deleted}=1；
 * 标签经 AI 生成后写回远端（content 末尾追加 #标签），{@code tags_synced} 标记是否已写回。
 */
@Data
@TableName("memos_note")
public class MemosNote {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Memos 笔记 UID（远端唯一标识，拉取去重键） */
    private String uid;

    /** 笔记原文（Markdown，入库时去除尾部 #标签 块保持纯净） */
    private String content;

    /** 当前有效标签（逗号分隔：远端解析 + AI 新增） */
    private String tags;

    /** 标签是否已写回远端：0 待写回 1 已同步（含无标签） */
    private Integer tagsSynced;

    /** 远端是否已删除：0 存活 1 标记删除 */
    private Integer remoteDeleted;

    /** 远端创建时间 */
    private LocalDateTime remoteCreateTime;

    /** 远端更新时间 */
    private LocalDateTime remoteUpdateTime;

    /** 本地入库时间 */
    private LocalDateTime createTime;

    /** 本地更新时间 */
    private LocalDateTime updateTime;
}
