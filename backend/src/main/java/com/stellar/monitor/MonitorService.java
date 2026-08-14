package com.stellar.monitor;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;
import com.sun.management.VMOption;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.NamedContributor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统监控服务：采集实时快照（JVM / 系统 / HTTP / HikariCP / 应用 / 健康）。
 * <p>全部数据来自本进程内存即可获得，不引入 Prometheus/Grafana；仅对内提供，登录可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    /** JVM 启动时刻（RuntimeMXBean 启动时间），用于计算启动耗时 */
    private final HttpRequestMetrics httpRequestMetrics;
    private final HealthContributorRegistry healthContributorRegistry;
    /** 懒加载 Hikari 数据源：监控页可在无数据源（纯演示场景）时降级展示空池 */
    private final ObjectProvider<HikariDataSource> hikariDataSourceProvider;

    /** 应用 Ready 时刻，用于计算启动耗时（Ready 事件 - JVM 启动） */
    private volatile long readyTimeMillis;

    /** 需要展示的 JVM 关键生效参数（排障/调优关注点） */
    private static final List<String> KEY_JVM_ARGS = List.of(
            "InitialHeapSize", "MaxHeapSize", "NewSize", "MaxNewSize",
            "MetaspaceSize", "MaxMetaspaceSize", "ThreadStackSize", "MaxDirectMemorySize",
            "UseG1GC", "UseParallelGC", "UseZGC", "UseSerialGC",
            "G1HeapRegionSize", "MaxGCPauseMillis", "ParallelGCThreads", "ConcGCThreads",
            "SurvivorRatio", "MaxTenuringThreshold",
            "HeapDumpOnOutOfMemoryError", "HeapDumpPath");

    /**
     * 组装监控概览。
     */
    public MonitorOverviewVO overview() {
        MonitorOverviewVO vo = new MonitorOverviewVO();
        vo.setJvm(jvmMetrics());
        vo.setSystem(systemMetrics());
        vo.setHttp(httpMetrics());
        vo.setHikariPool(hikariPoolMetrics());
        vo.setApp(appMetrics());
        vo.setHealth(healthMetrics());
        return vo;
    }

    /**
     * 生成 Markdown 监控报告（供导出下载，可直接粘贴给 AI 分析 JVM 调优）。
     * 内容为一次 overview() 快照 + 导出时间，中文可读排版。
     */
    public String exportMarkdown() {
        MonitorOverviewVO o = overview();
        StringBuilder sb = new StringBuilder();
        sb.append("# Stellar 系统监控报告\n\n");
        sb.append("- 导出时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append('\n');
        sb.append("- 运行时长: ").append(o.getApp().getUpTimeSeconds()).append(" 秒\n");
        sb.append("- 启动耗时: ").append(o.getApp().getStartCostMs()).append(" ms\n");
        sb.append("- 启动时间: ")
                .append(LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(o.getApp().getStartTimeMillis()),
                        java.time.ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append('\n');

        sb.append("\n## 健康状态\n\n");
        sb.append("- 整体: **").append(o.getHealth().getStatus()).append("**\n");
        o.getHealth().getComponents().forEach((name, status) ->
                sb.append("- ").append(name).append(": ").append(status).append('\n'));

        sb.append("\n## JVM\n\n");
        MonitorOverviewVO.JvmMetrics jvm = o.getJvm();
        sb.append("- 堆内存: 已用 ").append(bytes(jvm.getHeapUsed()))
                .append(" / 最大 ").append(bytes(jvm.getHeapMax()))
                .append(" / 使用率 ").append(pct(jvm.getHeapUsed(), jvm.getHeapMax())).append('\n');
        sb.append("- 非堆内存: 已用 ").append(bytes(jvm.getNonHeapUsed()))
                .append(" / 最大 ").append(bytes(jvm.getNonHeapMax())).append('\n');
        sb.append("- 线程: 活跃 ").append(jvm.getThreadActive())
                .append(" / 峰值 ").append(jvm.getThreadPeak())
                .append(" / 守护 ").append(jvm.getThreadDaemon()).append('\n');
        sb.append("- 已加载类: ").append(jvm.getLoadedClasses()).append('\n');
        sb.append("- Young GC: ").append(jvm.getYoungGcCount()).append(" 次 / ")
                .append(jvm.getYoungGcTimeMs()).append(" ms\n");
        sb.append("- Full GC: ").append(jvm.getFullGcCount()).append(" 次 / ")
                .append(jvm.getFullGcTimeMs()).append(" ms\n");
        if (jvm.getGcCollectors() != null) {
            sb.append("\n### GC 收集器明细\n\n");
            sb.append("| 收集器 | 次数 | 累计耗时(ms) | 平均单次(ms) |\n");
            sb.append("| --- | --- | --- | --- |\n");
            jvm.getGcCollectors().forEach(c -> sb.append("| ").append(c.getName())
                    .append(" | ").append(c.getCount())
                    .append(" | ").append(c.getTimeMs())
                    .append(" | ").append(c.getAvgTimeMs()).append(" |\n"));
        }
        if (jvm.getKeyJvmArgs() != null && !jvm.getKeyJvmArgs().isEmpty()) {
            sb.append("\n### JVM 关键生效参数\n\n");
            sb.append("| 参数 | 值 |\n");
            sb.append("| --- | --- |\n");
            jvm.getKeyJvmArgs().forEach(a -> sb.append("| ").append(a.getName())
                    .append(" | ").append(a.getValue()).append(" |\n"));
        }

        sb.append("\n## 系统资源\n\n");
        MonitorOverviewVO.SystemMetrics sys = o.getSystem();
        sb.append("- 进程 CPU: ").append(load(sys.getProcessCpuUsage())).append('\n');
        sb.append("- 系统 CPU: ").append(load(sys.getSystemCpuUsage())).append('\n');
        sb.append("- 磁盘: 总 ").append(bytes(sys.getDiskTotal()))
                .append(" / 剩余 ").append(bytes(sys.getDiskFree())).append('\n');
        sb.append("- 文件句柄: ")
                .append(sys.getFileOpenDescriptors() == null ? "N/A"
                        : sys.getFileOpenDescriptors() + " / " + sys.getFileMaxDescriptors())
                .append('\n');

        sb.append("\n## HTTP 请求\n\n");
        MonitorOverviewVO.HttpMetrics http = o.getHttp();
        sb.append("- 请求总数: ").append(http.getTotalRequests())
                .append("（2xx: ").append(http.getStatus2xx())
                .append(" / 4xx: ").append(http.getStatus4xx())
                .append(" / 5xx: ").append(http.getStatus5xx()).append("）\n");
        sb.append("- 最大耗时: ").append(http.getMaxCostMs()).append(" ms\n");
        sb.append("- 平均耗时: ").append(http.getAvgCostMs()).append(" ms\n");
        sb.append("- 当前活跃请求: ").append(http.getActiveRequests()).append('\n');

        sb.append("\n## 数据库连接池（HikariCP）\n\n");
        MonitorOverviewVO.HikariPoolMetrics pool = o.getHikariPool();
        sb.append("- 空闲连接: ").append(pool.getIdleConnections()).append('\n');
        sb.append("- 活跃连接: ").append(pool.getActiveConnections()).append('\n');
        sb.append("- 等待队列: ").append(pool.getPendingConnections()).append('\n');
        sb.append("- 池上限: ").append(pool.getMaximumPoolSize()).append('\n');

        return sb.toString();
    }

    private String bytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024L * 1024) return String.format("%.1f KB", n / 1024.0);
        if (n < 1024L * 1024 * 1024) return String.format("%.1f MB", n / 1024.0 / 1024);
        return String.format("%.2f GB", n / 1024.0 / 1024 / 1024);
    }

    private String pct(long used, long max) {
        if (max <= 0) return "-";
        return String.format("%.1f%%", used * 100.0 / max);
    }

    private String load(double load) {
        return load < 0 ? "未知" : String.format("%.1f%%", load * 100);
    }

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        readyTimeMillis = System.currentTimeMillis();
        log.info("系统监控已就绪，JVM 启动耗时={}ms",
                readyTimeMillis - ManagementFactory.getRuntimeMXBean().getStartTime());
    }

    // ===== JVM =====

    private MonitorOverviewVO.JvmMetrics jvmMetrics() {
        MonitorOverviewVO.JvmMetrics m = new MonitorOverviewVO.JvmMetrics();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        m.setHeapUsed(heap.getUsed());
        m.setHeapMax(heap.getMax());
        m.setHeapInit(heap.getInit());
        m.setNonHeapUsed(nonHeap.getUsed());
        m.setNonHeapMax(nonHeap.getMax());

        collectGcStats(m);

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        m.setThreadActive(threads.getThreadCount());
        m.setThreadDaemon(threads.getDaemonThreadCount());
        m.setThreadPeak(threads.getPeakThreadCount());

        ClassLoadingMXBean cl = ManagementFactory.getClassLoadingMXBean();
        m.setLoadedClasses(cl.getLoadedClassCount());
        m.setKeyJvmArgs(collectKeyJvmArgs());
        return m;
    }

    /**
     * 读 JVM 关键参数当前生效值（含默认值）：HotSpotDiagnosticMXBean 逐个查询，
     * 不存在的参数（如非 G1 时的 MaxGCPauseMillis）跳过，不阻断监控。
     */
    private List<MonitorOverviewVO.JvmArgStat> collectKeyJvmArgs() {
        List<MonitorOverviewVO.JvmArgStat> args = new ArrayList<>();
        try {
            HotSpotDiagnosticMXBean diag = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            for (String name : KEY_JVM_ARGS) {
                try {
                    VMOption option = diag.getVMOption(name);
                    MonitorOverviewVO.JvmArgStat stat = new MonitorOverviewVO.JvmArgStat();
                    stat.setName(name);
                    stat.setValue(option.getValue());
                    args.add(stat);
                } catch (IllegalArgumentException e) {
                    log.debug("JVM 参数 {} 当前 JVM 不支持，跳过: {}", name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("读取 JVM 关键参数失败（可能非 HotSpot）: {}", e.getMessage());
        }
        return args;
    }

    /**
     * GC 汇总与明细：按名称关键字归并 Young / Full。G1 为 "G1 Young Generation"/"G1 Old Generation"，
     * 并行回收为 "PS Scavenge"/"PS MarkSweep"，ZGC 为 "ZGC Minor"/"ZGC Major"。
     * 汇总之外保留每个收集器明细，供前端表格展示。
     */
    private void collectGcStats(MonitorOverviewVO.JvmMetrics m) {
        long youngCount = 0, youngTime = 0, fullCount = 0, fullTime = 0;
        List<MonitorOverviewVO.GcCollectorStat> collectors = new ArrayList<>();
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String name = bean.getName();
            long count = bean.getCollectionCount();
            long time = bean.getCollectionTime();
            boolean isYoung = name != null
                    && (name.contains("Young") || name.contains("Scavenge") || name.contains("Minor"));
            if (isYoung) {
                youngCount += count;
                youngTime += time;
            } else {
                fullCount += count;
                fullTime += time;
            }
            MonitorOverviewVO.GcCollectorStat stat = new MonitorOverviewVO.GcCollectorStat();
            stat.setName(name);
            stat.setCount(count);
            stat.setTimeMs(time);
            stat.setAvgTimeMs(count > 0 ? time / count : 0);
            collectors.add(stat);
        }
        m.setYoungGcCount(youngCount);
        m.setYoungGcTimeMs(youngTime);
        m.setFullGcCount(fullCount);
        m.setFullGcTimeMs(fullTime);
        m.setGcCollectors(collectors);
    }

    // ===== 系统 =====

    private MonitorOverviewVO.SystemMetrics systemMetrics() {
        MonitorOverviewVO.SystemMetrics m = new MonitorOverviewVO.SystemMetrics();
        OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        m.setProcessCpuUsage(clampLoad(os.getProcessCpuLoad()));
        m.setSystemCpuUsage(clampLoad(os.getSystemCpuLoad()));

        File root = new File("/");
        m.setDiskTotal(root.getTotalSpace());
        m.setDiskFree(root.getUsableSpace());

        collectFileDescriptors(m, os);
        return m;
    }

    /** 文件句柄：仅 Unix 平台（Linux 生产）支持，Windows 下异常降级为 null 前端显示 N/A */
    private void collectFileDescriptors(MonitorOverviewVO.SystemMetrics m, OperatingSystemMXBean os) {
        try {
            if (os instanceof UnixOperatingSystemMXBean unix) {
                m.setFileOpenDescriptors((long) unix.getOpenFileDescriptorCount());
                m.setFileMaxDescriptors((long) unix.getMaxFileDescriptorCount());
            }
        } catch (UnsupportedOperationException e) {
            log.warn("获取文件句柄失败（平台不支持）: {}", e.getMessage());
        }
    }

    private double clampLoad(double load) {
        return load < 0 ? -1.0 : Math.round(load * 10000) / 10000.0;
    }

    // ===== HTTP =====

    private MonitorOverviewVO.HttpMetrics httpMetrics() {
        HttpRequestMetrics.Snapshot s = httpRequestMetrics.snapshot();
        MonitorOverviewVO.HttpMetrics m = new MonitorOverviewVO.HttpMetrics();
        m.setTotalRequests(s.totalRequests);
        m.setStatus2xx(s.status2xx);
        m.setStatus4xx(s.status4xx);
        m.setStatus5xx(s.status5xx);
        m.setMaxCostMs(s.maxCostMs);
        m.setAvgCostMs(s.avgCostMs);
        m.setActiveRequests(s.activeRequests);
        return m;
    }

    // ===== HikariCP =====

    private MonitorOverviewVO.HikariPoolMetrics hikariPoolMetrics() {
        MonitorOverviewVO.HikariPoolMetrics m = new MonitorOverviewVO.HikariPoolMetrics();
        HikariDataSource ds = hikariDataSourceProvider.getIfAvailable();
        if (ds != null && ds.getHikariPoolMXBean() != null) {
            HikariPoolMXBean mxBean = ds.getHikariPoolMXBean();
            m.setIdleConnections(mxBean.getIdleConnections());
            m.setActiveConnections(mxBean.getActiveConnections());
            m.setPendingConnections(mxBean.getThreadsAwaitingConnection());
            m.setMaximumPoolSize(ds.getMaximumPoolSize());
        }
        return m;
    }

    // ===== 应用 =====

    private MonitorOverviewVO.AppMetrics appMetrics() {
        MonitorOverviewVO.AppMetrics m = new MonitorOverviewVO.AppMetrics();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        m.setUpTimeSeconds(runtime.getUptime() / 1000);
        m.setStartTimeMillis(runtime.getStartTime());
        m.setStartCostMs(readyTimeMillis > 0 ? Math.max(0, readyTimeMillis - runtime.getStartTime()) : 0);
        return m;
    }

    // ===== 健康 =====

    private MonitorOverviewVO.HealthMetrics healthMetrics() {
        MonitorOverviewVO.HealthMetrics m = new MonitorOverviewVO.HealthMetrics();
        Map<String, String> components = new LinkedHashMap<>();
        for (NamedContributor<HealthContributor> named : healthContributorRegistry) {
            String name = named.getName();
            // 对内探针（readiness/liveness）对排障无意义，不展示
            if ("readinessState".equals(name) || "livenessState".equals(name)) {
                continue;
            }
            collectHealth(name, named.getContributor(), components);
        }
        m.setStatus(aggregateStatus(components));
        m.setComponents(components);
        return m;
    }

    /** 递归展平健康组件（支持 CompositeHealthContributor） */
    private void collectHealth(String name, HealthContributor contributor, Map<String, String> out) {
        if (contributor instanceof HealthIndicator indicator) {
            Health health = safeHealth(indicator, name);
            out.put(name, health.getStatus().getCode());
        } else if (contributor instanceof CompositeHealthContributor composite) {
            for (NamedContributor<HealthContributor> child : composite) {
                collectHealth(name + "." + child.getName(), child.getContributor(), out);
            }
        }
    }

    private Health safeHealth(HealthIndicator indicator, String name) {
        try {
            return indicator.health();
        } catch (Exception e) {
            // 组件探针异常不应拖垮监控接口，降级为 DOWN 记录日志
            log.warn("健康组件 {} 探针异常: {}", name, e.getMessage());
            return Health.down(e).build();
        }
    }

    /** 整体状态：任一 DOWN → DOWN；否则全部 UP → UP；其余 UNKNOWN */
    private String aggregateStatus(Map<String, String> components) {
        String overall = "UP";
        for (String status : components.values()) {
            if ("DOWN".equals(status)) {
                return "DOWN";
            }
            if (!"UP".equals(status)) {
                overall = "UNKNOWN";
            }
        }
        return overall;
    }
}