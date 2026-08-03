package com.stellar.memos.vo;

import lombok.Data;

/**
 * 立即同步结果：拉取远端全部活跃笔记，upsert 本地；本地有而远端不存在 → 标记删除。
 */
@Data
public class MemosSyncResultVO {

    /** 远端拉取条数 */
    private int fetched;

    /** 本地新增 */
    private int created;

    /** 本地更新（内容/远端时间变化） */
    private int updated;

    /** 标记为远端已删 */
    private int markedDeleted;

    /** 拉取/写库失败条数 */
    private int errors;
}
