package com.stellar.ai.service;

import com.stellar.ai.entity.SysAiProvider;
import com.stellar.ai.entity.SysAiUsage;
import com.stellar.ai.mapper.SysAiProviderMapper;
import com.stellar.ai.mapper.SysAiUsageMapper;
import com.stellar.ai.vo.AiUsageStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SysAiUsageService} 单测：验证 token 记录（null 兜底为 0、insert 异常被吞不影响主流程）
 * 与统计聚合（总量/今日、按类型、按供应商联查名、近 7 日趋势补空白天、totals 为 null 的兜底安全）。
 * 两个 Mapper 用 Mockito 隔离，聚合 SQL 的结果由测试构造 Map 提供。
 */
@ExtendWith(MockitoExtension.class)
class SysAiUsageServiceTest {

    @Mock
    private SysAiUsageMapper usageMapper;
    @Mock
    private SysAiProviderMapper providerMapper;

    private SysAiUsageService service;

    @BeforeEach
    void setUp() {
        service = new SysAiUsageService(usageMapper, providerMapper);
    }

    // ===== record =====

    @Test
    void record_正常_写入并携带token() {
        service.record("account", "u1", 1L, "gpt-4", "TEXT", 10, 20, 30, "chat");
        ArgumentCaptor<SysAiUsage> cap = ArgumentCaptor.forClass(SysAiUsage.class);
        verify(usageMapper).insert(cap.capture());
        SysAiUsage u = cap.getValue();
        assertEquals(10, u.getPromptTokens());
        assertEquals(20, u.getCompletionTokens());
        assertEquals(30, u.getTotalTokens());
        assertEquals("TEXT", u.getModelType());
        assertNotNull(u.getCreateTime());
    }

    @Test
    void record_token为null_兜底为0() {
        service.record("ip", "1.2.3.4", null, "gpt-4", "TEXT", null, null, null, "chat");
        ArgumentCaptor<SysAiUsage> cap = ArgumentCaptor.forClass(SysAiUsage.class);
        verify(usageMapper).insert(cap.capture());
        SysAiUsage u = cap.getValue();
        assertEquals(0, u.getPromptTokens());
        assertEquals(0, u.getCompletionTokens());
        assertEquals(0, u.getTotalTokens());
    }

    @Test
    void record_insert抛异常_被吞不影响主流程() {
        doThrow(new RuntimeException("db down")).when(usageMapper).insert(any(SysAiUsage.class));
        assertDoesNotThrow(() -> service.record("account", "u1", 1L, "m", "TEXT", 1, 1, 2, "c"));
        verify(usageMapper).insert(any(SysAiUsage.class));
    }

    // ===== stats =====

    private Map<String, Object> totalsMap(long totalTokens, long totalCalls, long todayTokens, long todayCalls) {
        Map<String, Object> m = new HashMap<>();
        m.put("total_tokens", totalTokens);
        m.put("total_calls", totalCalls);
        m.put("today_tokens", todayTokens);
        m.put("today_calls", todayCalls);
        return m;
    }

    @Test
    void stats_totals为null_全量兜底0且趋势7天补零() {
        when(usageMapper.selectTotals(any())).thenReturn(null);
        when(usageMapper.selectByType()).thenReturn(List.of());
        when(usageMapper.selectByProvider()).thenReturn(List.of());
        when(usageMapper.selectDailyTrend(any())).thenReturn(List.of());

        AiUsageStatsVO vo = service.stats();
        assertEquals(0, vo.getTotalTokens());
        assertEquals(0, vo.getTotalCalls());
        assertEquals(0, vo.getTodayTokens());
        assertEquals(0, vo.getTodayCalls());
        assertEquals(0, vo.getByType().size());
        assertEquals(0, vo.getByProvider().size());
        assertEquals(7, vo.getDailyTrend().size());
        assertTrue(vo.getDailyTrend().stream().allMatch(d -> d.getTokens() == 0 && d.getCalls() == 0));
    }

    @Test
    void stats_全量数据_聚合正确且联查供应商名() {
        when(usageMapper.selectTotals(any())).thenReturn(totalsMap(100L, 5L, 20L, 2L));

        Map<String, Object> t1 = new HashMap<>();
        t1.put("model_type", "TEXT");
        t1.put("tokens", 50L);
        t1.put("calls", 3L);
        when(usageMapper.selectByType()).thenReturn(List.of(t1));

        Map<String, Object> p1 = new HashMap<>();
        p1.put("provider_id", 1L);
        p1.put("tokens", 80L);
        p1.put("calls", 4L);
        when(usageMapper.selectByProvider()).thenReturn(List.of(p1));

        SysAiProvider prov = new SysAiProvider();
        prov.setId(1L);
        prov.setName("OpenAI");
        when(providerMapper.selectBatchIds(any())).thenReturn(List.of(prov));

        String today = LocalDate.now().toString();
        Map<String, Object> d1 = new HashMap<>();
        d1.put("d", today);
        d1.put("tokens", 9L);
        d1.put("calls", 1L);
        when(usageMapper.selectDailyTrend(any())).thenReturn(List.of(d1));

        AiUsageStatsVO vo = service.stats();
        assertEquals(100, vo.getTotalTokens());
        assertEquals(5, vo.getTotalCalls());
        assertEquals(20, vo.getTodayTokens());
        assertEquals(2, vo.getTodayCalls());
        assertEquals(1, vo.getByType().size());
        assertEquals("TEXT", vo.getByType().get(0).getModelType());
        assertEquals(50, vo.getByType().get(0).getTokens());
        assertEquals(1, vo.getByProvider().size());
        assertEquals("OpenAI", vo.getByProvider().get(0).getProviderName());
        assertEquals(7, vo.getDailyTrend().size());
        AiUsageStatsVO.DailyPoint todayPoint = vo.getDailyTrend().stream()
                .filter(d -> d.getDate().equals(today)).findFirst().orElseThrow();
        assertEquals(9, todayPoint.getTokens());
    }

    @Test
    void stats_供应商无匹配名_置null不报错() {
        when(usageMapper.selectTotals(any())).thenReturn(totalsMap(10L, 1L, 1L, 1L));
        when(usageMapper.selectByType()).thenReturn(List.of());
        Map<String, Object> p1 = new HashMap<>();
        p1.put("provider_id", 99L);
        p1.put("tokens", 5L);
        p1.put("calls", 1L);
        when(usageMapper.selectByProvider()).thenReturn(List.of(p1));
        when(providerMapper.selectBatchIds(any())).thenReturn(List.of()); // 无该供应商
        when(usageMapper.selectDailyTrend(any())).thenReturn(List.of());

        AiUsageStatsVO vo = service.stats();
        assertEquals(1, vo.getByProvider().size());
        assertNull(vo.getByProvider().get(0).getProviderName());
    }
}
