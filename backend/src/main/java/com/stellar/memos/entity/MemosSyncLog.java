package com.stellar.memos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 备忘同步状态记录：每次定时/手动「立即同步」落一条，用户在备忘页查看；3 天前的记录自动清理。
 */
@Data
@TableName("memos_sync_log")
public class MemosSyncLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 触发方式：scheduled 定时 / manual 手动 */
    private String triggerType;

    /** 状态：success / partial(部分失败) / failed / skipped(未配置跳过) */
    private String status;

    /** 远端拉取条数 */
    private Integer fetched;

    /** 本地新增条数 */
    private Integer created;

    /** 本地更新条数 */
    private Integer updated;

    /** 标记远端已删条数 */
    private Integer markedDeleted;

    /** 拉取/写库失败条数 */
    private Integer errors;

    /** 同步耗时(毫秒) */
    private Long durationMs;

    /** 失败原因(status=failed 时) */
    private String errorMessage;

    private LocalDateTime createTime;
}
