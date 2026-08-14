package com.stellar.monitor;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP 请求实时计数器：由 {@link com.stellar.interceptor.RequestLogInterceptor} 驱动，
 * 记录请求总数 / 状态码分布（2xx·4xx·5xx）/ 最大与平均耗时 / 当前活跃请求数。
 * <p>使用原子计数保证并发安全；监控页自身轮询（/monitor/**）与探针（/actuator/**）不计入，
 * 避免自污染统计。
 */
@Component
public class HttpRequestMetrics {

    private static final String MONITOR_PREFIX = "/monitor";
    private static final String ACTUATOR_PREFIX = "/actuator";

    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong status2xx = new AtomicLong(0);
    private final AtomicLong status4xx = new AtomicLong(0);
    private final AtomicLong status5xx = new AtomicLong(0);
    private final AtomicLong maxCostMs = new AtomicLong(0);
    private final AtomicLong costSumMs = new AtomicLong(0);
    private final AtomicLong costCount = new AtomicLong(0);

    /** 请求进入：仅当 URI 不在排除清单时递增活跃数。 */
    public void requestIn(String uri) {
        if (shouldCount(uri)) {
            activeRequests.incrementAndGet();
        }
    }

    /**
     * 请求完成：PUT 语义只会被调用一次（afterCompletion 所有路径都会执行），
     * 更新总数 / 状态桶 / 耗时统计并递减活跃数。
     */
    public void requestOut(String uri, int status, long elapsedMs) {
        if (!shouldCount(uri)) {
            return;
        }
        activeRequests.decrementAndGet();
        totalRequests.incrementAndGet();
        if (status >= 500) {
            status5xx.incrementAndGet();
        } else if (status >= 400) {
            status4xx.incrementAndGet();
        } else if (status >= 200 && status < 300) {
            status2xx.incrementAndGet();
        }
        if (elapsedMs >= 0) {
            updateMax(elapsedMs);
            costSumMs.addAndGet(elapsedMs);
            costCount.incrementAndGet();
        }
    }

    /** 采集快照（供 {@link MonitorService} 组装 VO）。 */
    public Snapshot snapshot() {
        Snapshot snap = new Snapshot();
        snap.totalRequests = totalRequests.get();
        snap.status2xx = status2xx.get();
        snap.status4xx = status4xx.get();
        snap.status5xx = status5xx.get();
        snap.maxCostMs = maxCostMs.get();
        long count = costCount.get();
        snap.avgCostMs = count > 0 ? Math.round(costSumMs.get() / (double) count) : 0;
        snap.activeRequests = activeRequests.get();
        return snap;
    }

    /** 重置（供测试用）。 */
    public void reset() {
        activeRequests.set(0);
        totalRequests.set(0);
        status2xx.set(0);
        status4xx.set(0);
        status5xx.set(0);
        maxCostMs.set(0);
        costSumMs.set(0);
        costCount.set(0);
    }

    private boolean shouldCount(String uri) {
        if (uri == null) {
            return false;
        }
        return !uri.startsWith(MONITOR_PREFIX) && !uri.startsWith(ACTUATOR_PREFIX);
    }

    private void updateMax(long elapsedMs) {
        while (true) {
            long current = maxCostMs.get();
            if (elapsedMs <= current || maxCostMs.compareAndSet(current, elapsedMs)) {
                return;
            }
        }
    }

    /** HTTP 指标瞬时快照。 */
    public static class Snapshot {
        public long totalRequests;
        public long status2xx;
        public long status4xx;
        public long status5xx;
        public long maxCostMs;
        public long avgCostMs;
        public int activeRequests;
    }
}