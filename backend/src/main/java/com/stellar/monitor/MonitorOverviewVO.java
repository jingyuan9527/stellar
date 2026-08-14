package com.stellar.monitor;

import lombok.Data;

import java.util.Map;

/**
 * 系统监控概览（实时快照）：JVM / 系统 / HTTP / 连接池 / 应用 / 健康。
 * <p>全部为当前瞬时值，无历史时序；前端固定 3-5s 轮询本接口刷新。
 */
@Data
public class MonitorOverviewVO {

    /** JVM 内存 / GC / 线程 / 类加载 */
    private JvmMetrics jvm;

    /** 进程与系统 CPU、磁盘、文件句柄 */
    private SystemMetrics system;

    /** HTTP 请求统计（由 {@link HttpRequestMetrics} 拦截器实时计数） */
    private HttpMetrics http;

    /** HikariCP 连接池 */
    private HikariPoolMetrics hikariPool;

    /** 应用启动信息 */
    private AppMetrics app;

    /** Actuator 健康（组件名 → 状态） */
    private HealthMetrics health;

    /** JVM 虚拟机指标 */
    @Data
    public static class JvmMetrics {
        /** 堆已用字节 */
        private long heapUsed;
        /** 堆最大字节 */
        private long heapMax;
        /** 已初始化堆字节 */
        private long heapInit;
        /** 元空间等非堆已用字节 */
        private long nonHeapUsed;
        /** 非堆最大字节，-1 表示无上限 */
        private long nonHeapMax;
        /** Young GC 累计次数 */
        private long youngGcCount;
        /** Young GC 累计耗时 ms */
        private long youngGcTimeMs;
        /** Full GC 累计次数 */
        private long fullGcCount;
        /** Full GC 累计耗时 ms */
        private long fullGcTimeMs;
        /** 当前活动线程数 */
        private long threadActive;
        /** 守护线程数 */
        private long threadDaemon;
        /** 线程峰值 */
        private long threadPeak;
        /** 已加载类数 */
        private long loadedClasses;
        /** 各 GC 收集器明细（名称/次数/耗时/平均单次耗时） */
        private java.util.List<GcCollectorStat> gcCollectors;
        /** JVM 关键生效参数（HotSpotDiagnosticMXBean 读生效值，含默认） */
        private java.util.List<JvmArgStat> keyJvmArgs;
    }

    /** JVM 生效参数条目 */
    @Data
    public static class JvmArgStat {
        /** 参数名（MaxHeapSize 等） */
        private String name;
        /** 当前生效值（字符串，字节数/布尔等） */
        private String value;
    }

    /** 单个 GC 收集器统计 */
    @Data
    public static class GcCollectorStat {
        /** 收集器名称（G1 Young Generation 等） */
        private String name;
        /** 累计次数 */
        private long count;
        /** 累计耗时 ms */
        private long timeMs;
        /** 平均单次耗时 ms（count=0 时为 0） */
        private long avgTimeMs;
    }

    /** 操作系统指标 */
    @Data
    public static class SystemMetrics {
        /** 当前进程 CPU 使用率 0-1，未知为 -1 */
        private double processCpuUsage;
        /** 系统整体 CPU 使用率 0-1，未知为 -1 */
        private double systemCpuUsage;
        /** 磁盘总字节 */
        private long diskTotal;
        /** 磁盘可用字节 */
        private long diskFree;
        /** 打开文件句柄数，平台不支持时为 null */
        private Long fileOpenDescriptors;
        /** 最大文件句柄数，平台不支持时为 null */
        private Long fileMaxDescriptors;
    }

    /** HTTP 请求统计 */
    @Data
    public static class HttpMetrics {
        /** 累计请求总数 */
        private long totalRequests;
        /** 2xx 计数 */
        private long status2xx;
        /** 4xx 计数 */
        private long status4xx;
        /** 5xx 计数 */
        private long status5xx;
        /** 最大耗时 ms（累计窗口内） */
        private long maxCostMs;
        /** 平均耗时 ms */
        private long avgCostMs;
        /** 当前活跃（处理中）请求数 */
        private int activeRequests;
    }

    /** HikariCP 连接池 */
    @Data
    public static class HikariPoolMetrics {
        /** 空闲连接数 */
        private int idleConnections;
        /** 活跃使用连接数 */
        private int activeConnections;
        /** 等待获取连接的排队数，>0 表示连接池打满 */
        private int pendingConnections;
        /** 连接池最大上限 */
        private int maximumPoolSize;
    }

    /** 应用信息 */
    @Data
    public static class AppMetrics {
        /** 已运行秒数 */
        private long upTimeSeconds;
        /** 启动耗时 ms（Ready 事件 - JVM 启动） */
        private long startCostMs;
        /** JVM 启动 epoch ms，前端格式化展示 */
        private long startTimeMillis;
    }

    /** 健康状态 */
    @Data
    public static class HealthMetrics {
        /** 整体状态 UP / DOWN / UNKNOWN */
        private String status;
        /** 组件名 → 状态（db / diskSpace / ping / jvm 等） */
        private Map<String, String> components;
    }
}