package com.stellar.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HttpRequestMetrics} 单测：状态桶分类、耗时统计、活跃数、自污染排除。
 */
class HttpRequestMetricsTest {

    HttpRequestMetrics metrics;

    @BeforeEach
    void setup() {
        metrics = new HttpRequestMetrics();
    }

    @Test
    void 状态码分类与总数() {
        metrics.requestIn("/system/log");
        metrics.requestOut("/system/log", 200, 10);
        metrics.requestIn("/user/info");
        metrics.requestOut("/user/info", 404, 20);
        metrics.requestIn("/game/math");
        metrics.requestOut("/game/math", 500, 30);
        metrics.requestIn("/tools/json");
        metrics.requestOut("/tools/json", 302, 40);

        HttpRequestMetrics.Snapshot s = metrics.snapshot();
        assertEquals(4, s.totalRequests, "302 仅计入 total");
        assertEquals(1, s.status2xx);
        assertEquals(1, s.status4xx);
        assertEquals(1, s.status5xx);
    }

    @Test
    void 活跃请求进入未完成时递增() {
        metrics.requestIn("/a");
        metrics.requestIn("/b");
        assertEquals(2, metrics.snapshot().activeRequests);
        metrics.requestOut("/a", 200, 5);
        assertEquals(1, metrics.snapshot().activeRequests);
    }

    @Test
    void 最大与平均耗时() {
        metrics.requestIn("/a");
        metrics.requestOut("/a", 200, 100);
        metrics.requestIn("/b");
        metrics.requestOut("/b", 200, 300);
        HttpRequestMetrics.Snapshot s = metrics.snapshot();
        assertEquals(300, s.maxCostMs);
        assertEquals(200, s.avgCostMs);
    }

    @Test
    void 监控与探针请求不计数() {
        metrics.requestIn("/monitor/overview");
        metrics.requestOut("/monitor/overview", 200, 5);
        metrics.requestIn("/actuator/health");
        metrics.requestOut("/actuator/health", 200, 5);
        assertEquals(0, metrics.snapshot().totalRequests);
        assertEquals(0, metrics.snapshot().activeRequests);
    }

    @Test
    void 空窗口快照为0() {
        HttpRequestMetrics.Snapshot s = metrics.snapshot();
        assertEquals(0, s.totalRequests);
        assertEquals(0, s.avgCostMs);
        assertEquals(0, s.activeRequests);
    }
}