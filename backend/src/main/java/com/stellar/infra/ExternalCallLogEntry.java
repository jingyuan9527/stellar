package com.stellar.infra;

import lombok.Builder;
import lombok.Data;

/**
 * 一次外部调用的日志快照。infra 层的中立模型，不依赖任何特性模块的实体；
 * 由 {@link CallLogSink} 实现方负责映射到各自的存储结构。
 */
@Data
@Builder
public class ExternalCallLogEntry {

    private String provider;

    private String action;

    /** 调用入参（已按上限截断） */
    private String params;

    private boolean success;

    /** 失败信息（已按上限截断），成功时为 null */
    private String errorMsg;

    private long durationMs;

    /** 同步阶段已确定的操作人标识；登录场景为 null，由 sink 实现方异步解析用户名 */
    private String operator;

    /** 登录用户的 userId；未登录/异常时为 null */
    private Long operatorUserId;

    private String ip;
}
