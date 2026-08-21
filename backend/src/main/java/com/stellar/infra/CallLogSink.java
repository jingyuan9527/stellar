package com.stellar.infra;

/**
 * 外部调用日志的落库出口。infra 只定义缝，具体写到哪张表由特性模块提供实现并注册，
 * 避免 infra 反向依赖业务模块。
 */
public interface CallLogSink {

    void write(ExternalCallLogEntry entry);
}
