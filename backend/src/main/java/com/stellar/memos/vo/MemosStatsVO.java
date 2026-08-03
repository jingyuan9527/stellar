package com.stellar.memos.vo;

import lombok.Data;

/**
 * 备忘同步统计（总数/存活/已删/待打标/待写回）。
 */
@Data
public class MemosStatsVO {

    /** 本地备份总数 */
    private long total;

    /** 远端存活 */
    private long active;

    /** 远端已删（标记删除） */
    private long deleted;

    /** 待打标签（存活且无标签） */
    private long untagged;

    /** 待写回标签（存活且 tags_synced=0） */
    private long pendingPush;
}
