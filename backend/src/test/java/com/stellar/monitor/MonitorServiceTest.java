package com.stellar.monitor;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.DefaultHealthContributorRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link MonitorService} 单测：快照组装、Hikari 降级、健康聚合与内置探针过滤。
 * <p>JVM/系统/应用读数走本进程真实 ManagementFactory（本地运行即可），健康与连接池用 mock 隔离。
 */
@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

    @Mock
    HttpRequestMetrics httpRequestMetrics;
    @Mock
    ObjectProvider<HikariDataSource> hikariProvider;
    @Mock
    HikariDataSource hikariDataSource;
    @Mock
    HikariPoolMXBean hikariPoolMXBean;

    MonitorService service;

    @BeforeEach
    void setup() {
        // 默认空快照，避免未显式 stubbing 时 NPE
        lenient().when(httpRequestMetrics.snapshot()).thenReturn(new HttpRequestMetrics.Snapshot());
        service = new MonitorService(httpRequestMetrics, registryWithIndicators(), hikariProvider);
    }

    private HealthContributorRegistry registryWithIndicators() {
        return new DefaultHealthContributorRegistry();
    }

    @Test
    void overview_各分区均组装() {
        HttpRequestMetrics.Snapshot snap = new HttpRequestMetrics.Snapshot();
        snap.totalRequests = 100;
        snap.status2xx = 90;
        snap.status4xx = 8;
        snap.status5xx = 2;
        snap.maxCostMs = 500;
        snap.avgCostMs = 30;
        snap.activeRequests = 3;
        when(httpRequestMetrics.snapshot()).thenReturn(snap);

        MonitorOverviewVO vo = service.overview();
        assertNotNull(vo.getJvm());
        assertTrue(vo.getJvm().getHeapUsed() >= 0);
        assertTrue(vo.getJvm().getThreadActive() > 0);
        assertTrue(vo.getJvm().getLoadedClasses() > 0);
        assertTrue(vo.getJvm().getYoungGcCount() >= 0);
        assertNotNull(vo.getJvm().getGcCollectors());
        assertTrue(vo.getJvm().getGcCollectors().size() >= 1);
        assertTrue(vo.getJvm().getGcCollectors().get(0).getCount() >= 0);
        assertTrue(vo.getJvm().getGcCollectors().get(0).getAvgTimeMs() >= 0);
        assertNotNull(vo.getJvm().getKeyJvmArgs());
        assertTrue(vo.getJvm().getKeyJvmArgs().size() >= 1);
        assertNotNull(vo.getJvm().getKeyJvmArgs().get(0).getName());
        assertNotNull(vo.getJvm().getKeyJvmArgs().get(0).getValue());

        assertNotNull(vo.getSystem());
        assertTrue(vo.getSystem().getDiskTotal() > 0);
        assertTrue(vo.getSystem().getDiskFree() >= 0);

        assertEquals(100, vo.getHttp().getTotalRequests());
        assertEquals(90, vo.getHttp().getStatus2xx());
        assertEquals(8, vo.getHttp().getStatus4xx());
        assertEquals(2, vo.getHttp().getStatus5xx());
        assertEquals(500, vo.getHttp().getMaxCostMs());
        assertEquals(3, vo.getHttp().getActiveRequests());

        assertNotNull(vo.getHikariPool());
        assertNotNull(vo.getApp());
        assertTrue(vo.getApp().getUpTimeSeconds() >= 0);
        assertTrue(vo.getApp().getStartTimeMillis() > 0);
        assertTrue(vo.getApp().getStartCostMs() >= 0);

        assertNotNull(vo.getHealth());
    }

    @Test
    void overview_慢变量走缓存复用同一实例() {
        MonitorOverviewVO first = service.overview();
        MonitorOverviewVO second = service.overview();
        assertSame(first.getSystem(), second.getSystem());
        assertSame(first.getJvm().getKeyJvmArgs(), second.getJvm().getKeyJvmArgs());
    }

    @Test
    void sampleSlowMetrics_更新缓存() {
        MonitorOverviewVO.SystemMetrics oldSys = service.overview().getSystem();
        service.sampleSlowMetrics();
        MonitorOverviewVO.SystemMetrics newSys = service.overview().getSystem();
        assertNotNull(newSys);
        assertTrue(newSys.getDiskTotal() > 0);
        assertNotSame(oldSys, newSys);
        assertTrue(newSys.getProcessCpuUsage() >= -1);
    }

    @Test
    void hikari_无数据源时降级不炸() {
        when(hikariProvider.getIfAvailable()).thenReturn(null);
        MonitorOverviewVO vo = service.overview();
        assertEquals(0, vo.getHikariPool().getIdleConnections());
        assertEquals(0, vo.getHikariPool().getActiveConnections());
        assertEquals(0, vo.getHikariPool().getPendingConnections());
        assertNotNull(vo.getHealth());
        assertEquals("UP", vo.getHealth().getStatus());
    }

    @Test
    void hikari_透传池读数() {
        when(hikariProvider.getIfAvailable()).thenReturn(hikariDataSource);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(hikariPoolMXBean);
        when(hikariPoolMXBean.getIdleConnections()).thenReturn(5);
        when(hikariPoolMXBean.getActiveConnections()).thenReturn(3);
        when(hikariPoolMXBean.getThreadsAwaitingConnection()).thenReturn(1);
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(10);

        MonitorOverviewVO.HikariPoolMetrics m = service.overview().getHikariPool();
        assertEquals(5, m.getIdleConnections());
        assertEquals(3, m.getActiveConnections());
        assertEquals(1, m.getPendingConnections());
        assertEquals(10, m.getMaximumPoolSize());
    }

    @Test
    void health_全UP聚合UP并过滤内置探针() {
        HealthContributorRegistry registry = new DefaultHealthContributorRegistry();
        registry.registerContributor("db", (HealthIndicator) () -> Health.up().build());
        registry.registerContributor("readinessState", (HealthIndicator) () -> Health.down().build());
        registry.registerContributor("livenessState", (HealthIndicator) () -> Health.down().build());
        service = new MonitorService(httpRequestMetrics, registry, hikariProvider);

        MonitorOverviewVO.HealthMetrics h = service.overview().getHealth();
        assertEquals("UP", h.getStatus());
        assertEquals(1, h.getComponents().size());
        assertEquals("UP", h.getComponents().get("db"));
    }

    @Test
    void health_任一DOWN整体DOWN() {
        HealthContributorRegistry registry = new DefaultHealthContributorRegistry();
        registry.registerContributor("db", (HealthIndicator) () -> Health.up().build());
        registry.registerContributor("diskSpace", (HealthIndicator) () -> Health.down().build());
        service = new MonitorService(httpRequestMetrics, registry, hikariProvider);

        MonitorOverviewVO.HealthMetrics h = service.overview().getHealth();
        assertEquals("DOWN", h.getStatus());
        assertEquals("UP", h.getComponents().get("db"));
        assertEquals("DOWN", h.getComponents().get("diskSpace"));
    }

    @Test
    void health_composite展平() {
        CompositeHealthContributor composite = CompositeHealthContributor.fromMap(
                Map.of("sub", (HealthIndicator) () -> Health.up().build()));
        HealthContributorRegistry registry = new DefaultHealthContributorRegistry();
        registry.registerContributor("redis", composite);
        service = new MonitorService(httpRequestMetrics, registry, hikariProvider);

        MonitorOverviewVO.HealthMetrics h = service.overview().getHealth();
        assertEquals("UP", h.getComponents().get("redis.sub"));
    }

    @Test
    void health_探针异常降级DOWN不炸() {
        HealthContributorRegistry registry = new DefaultHealthContributorRegistry();
        registry.registerContributor("db", (HealthIndicator) () -> { throw new IllegalStateException("boom"); });
        service = new MonitorService(httpRequestMetrics, registry, hikariProvider);

        MonitorOverviewVO.HealthMetrics h = service.overview().getHealth();
        assertEquals("DOWN", h.getStatus());
        assertEquals("DOWN", h.getComponents().get("db"));
    }

    @Test
    void exportMarkdown_包含关键区块() {
        String md = service.exportMarkdown();
        assertTrue(md.startsWith("# Stellar 系统监控报告"));
        assertTrue(md.contains("## 健康状态"));
        assertTrue(md.contains("## JVM"));
        assertTrue(md.contains("## 系统资源"));
        assertTrue(md.contains("## HTTP 请求"));
        assertTrue(md.contains("## 数据库连接池"));
        assertTrue(md.contains("导出时间"));
    }
}