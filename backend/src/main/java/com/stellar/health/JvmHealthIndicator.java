package com.stellar.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;

/**
 * JVM 健康指标：监控堆/非堆内存与 CPU 使用情况。
 * 挂载到 Actuator /actuator/health 的 jvm 组件。
 */
@Component
public class JvmHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMxBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMxBean.getNonHeapMemoryUsage();
        OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean sunOsMxBean =
                (com.sun.management.OperatingSystemMXBean) osMxBean;

        // 堆用量比例 > 90% 视为不健康（DOWN），其余为 UP——比例式告警而非绝对值
        double heapUsedPercent = heap.getMax() > 0
                ? (double) heap.getUsed() / heap.getMax() * 100
                : 0;
        double heapInitPercent = heap.getMax() > 0
                ? (double) heap.getInit() / heap.getMax() * 100
                : 0;
        String heapStatus = heapUsedPercent > 90 ? "DOWN" : "UP";

        return new Health.Builder()
                .status(heapStatus)
                .withDetail("heap.used", heap.getUsed())
                .withDetail("heap.max", heap.getMax())
                .withDetail("heap.usedPercent", String.format("%.1f%%", heapUsedPercent))
                .withDetail("heap.initPercent", String.format("%.1f%%", heapInitPercent))
                .withDetail("nonHeap.used", nonHeap.getUsed())
                .withDetail("nonHeap.max", nonHeap.getMax() < 0 ? "unbounded" : nonHeap.getMax())
                .withDetail("cpu.processLoad", formatLoad(sunOsMxBean.getProcessCpuLoad()))
                .withDetail("cpu.systemLoad", formatLoad(osMxBean.getSystemLoadAverage()))
                .withDetail("cpu.availableProcessors", osMxBean.getAvailableProcessors())
                .withDetail("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime())
                .build();
    }

    private String formatLoad(double load) {
        // getProcessCpuLoad 在首次调用返回 -1；pct 化统一展示
        return load < 0 ? "unknown" : String.format("%.1f%%", load * 100);
    }
}
