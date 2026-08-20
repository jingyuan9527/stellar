package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.stellar.ai.service.SysAiUsageService;
import com.stellar.ai.vo.AiUsageStatsVO;
import com.stellar.system.entity.SysFile;
import com.stellar.system.mapper.SysFileMapper;
import com.stellar.system.vo.DashboardStatsVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.stellar.ai.mapper.AiTaskMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link DashboardService} 单测：各模块统计聚合 + 异常分支吞掉独立降级。
 * <p>任务/TTS 统计走 {@link AiTaskMapper} 聚合方法（selectTaskStatTotals/selectTaskStatRecent/selectTtsStat），
 * 文件统计仍走 selectList。聚合 SQL 本身由 DB 层保证，此处验证聚合结果正确映射到 VO。
 * <p>LambdaQueryWrapper.select() 需在构建时解析实体列，须先经 TableInfoHelper 注册 SysFile 元数据
 * （Spring 启动时由 MyBatis-Plus 自动做，纯 Mockito 下手工补）。
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    SysAiUsageService aiUsageService;
    @Mock
    AiTaskMapper aiTaskMapper;
    @Mock
    SysFileMapper fileMapper;

    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysFile.class);
    }

    private Map<String, Object> taskTotals(long total, long success, long durationSum, long durationCount) {
        return Map.of("total", total, "success", success, "duration_sum", durationSum, "duration_count", durationCount);
    }

    private Map<String, Object> taskRecent(long today, long week, long prev) {
        return Map.of("today", today, "week_total", week, "prev_week_total", prev);
    }

    private Map<String, Object> ttsStat(long total, long today, long size) {
        return Map.of("total", total, "today", today, "total_size", size);
    }

    /** 默认空聚合：任务/TTS 全部为 0。lenient：部分测试会覆盖其中个别 stub，未覆盖时按默认生效。 */
    private void stubEmptyTaskAggregates() {
        lenient().when(aiTaskMapper.selectTaskStatTotals(anyString(), anyString())).thenReturn(taskTotals(0, 0, 0, 0));
        lenient().when(aiTaskMapper.selectTaskStatRecent(anyString(), any(), any(), any())).thenReturn(taskRecent(0, 0, 0));
        lenient().when(aiTaskMapper.selectTtsStat(any())).thenReturn(ttsStat(0, 0, 0));
        lenient().when(fileMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
    }

    @Test
    void stats_正常聚合所有模块() {
        DashboardService service = new DashboardService(aiUsageService, aiTaskMapper, fileMapper);
        when(aiUsageService.stats()).thenReturn(new AiUsageStatsVO());
        stubEmptyTaskAggregates();
        when(aiTaskMapper.selectTaskStatTotals("text", "success")).thenReturn(taskTotals(2, 1, 100, 1));
        when(aiTaskMapper.selectTaskStatRecent(eq("text"), any(), any(), any())).thenReturn(taskRecent(1, 2, 0));
        when(aiTaskMapper.selectTaskStatTotals("image", "completed")).thenReturn(taskTotals(1, 1, 500, 1));
        when(aiTaskMapper.selectTaskStatRecent(eq("image"), any(), any(), any())).thenReturn(taskRecent(1, 1, 0));
        when(aiTaskMapper.selectTaskStatTotals("video", "completed")).thenReturn(taskTotals(1, 1, 1000, 1));
        when(aiTaskMapper.selectTaskStatRecent(eq("video"), any(), any(), any())).thenReturn(taskRecent(1, 1, 0));
        when(aiTaskMapper.selectTtsStat(any())).thenReturn(ttsStat(1, 1, 0));

        DashboardStatsVO vo = service.stats();

        assertNotNull(vo.getAiUsage());
        // text: 2 条终态（generating 已被 SQL 排除），1 成功
        assertEquals(2, vo.getTextGen().getTotal());
        assertEquals(1, vo.getTextGen().getSuccessCount());
        assertEquals(50.0, vo.getTextGen().getSuccessRate());
        assertEquals(100, vo.getTextGen().getAvgDuration());
        // 近 7 日 2 条（今天 + 前天），无前 7 日
        assertEquals(2, vo.getTextGen().getWeekTotal());
        assertEquals(0, vo.getTextGen().getPrevWeekTotal());
        assertEquals(1, vo.getImageTask().getSuccessCount());
        assertEquals(1, vo.getImageTask().getWeekTotal());
        assertEquals(1, vo.getVideoTask().getSuccessCount());
        assertNotNull(vo.getFile());
        assertNotNull(vo.getTts());
    }

    @Test
    void buildTaskStat_本周与上周边界() {
        DashboardService service = new DashboardService(aiUsageService, aiTaskMapper, fileMapper);
        when(aiUsageService.stats()).thenReturn(new AiUsageStatsVO());
        stubEmptyTaskAggregates();
        when(aiTaskMapper.selectTaskStatTotals("text", "success")).thenReturn(taskTotals(4, 4, 0, 0));
        when(aiTaskMapper.selectTaskStatRecent(eq("text"), any(), any(), any())).thenReturn(taskRecent(1, 2, 2));

        DashboardStatsVO vo = service.stats();

        assertEquals(2, vo.getTextGen().getWeekTotal());
        assertEquals(2, vo.getTextGen().getPrevWeekTotal());
    }

    @Test
    void stats_文件按类型分组() {
        DashboardService service = new DashboardService(aiUsageService, aiTaskMapper, fileMapper);
        when(aiUsageService.stats()).thenReturn(new AiUsageStatsVO());
        stubEmptyTaskAggregates();

        SysFile png = new SysFile();
        png.setExt("png");
        png.setSize(100L);
        png.setCreateTime(LocalDateTime.now());
        SysFile mp3 = new SysFile();
        mp3.setExt("mp3");
        mp3.setSize(200L);
        SysFile unknown = new SysFile();
        unknown.setExt("txt");
        unknown.setSize(50L);
        when(fileMapper.selectList(any(Wrapper.class))).thenReturn(List.of(png, mp3, unknown));

        DashboardStatsVO vo = service.stats();

        DashboardStatsVO.FileStat file = vo.getFile();
        assertEquals(3, file.getTotal());
        assertEquals(1, file.getTodayUpload());
        assertEquals(350L, file.getTotalSize());
        assertEquals(3, file.getByType().size());
    }

    @Test
    void stats_单模块异常_独立降级其余继续() {
        DashboardService service = new DashboardService(aiUsageService, aiTaskMapper, fileMapper);
        when(aiUsageService.stats()).thenThrow(new RuntimeException("usage down"));
        when(aiTaskMapper.selectTaskStatTotals(anyString(), anyString())).thenThrow(new RuntimeException("task down"));
        when(aiTaskMapper.selectTtsStat(any())).thenThrow(new RuntimeException("task down"));
        when(fileMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        DashboardStatsVO vo = service.stats();

        assertNull(vo.getAiUsage());
        assertNull(vo.getTextGen());
        assertNull(vo.getImageTask());
        assertNull(vo.getVideoTask());
        assertNull(vo.getTts());
        assertNotNull(vo.getFile());
    }

    @Test
    void buildTaskStat_无终态_成功率0() {
        DashboardService service = new DashboardService(aiUsageService, aiTaskMapper, fileMapper);
        when(aiUsageService.stats()).thenReturn(new AiUsageStatsVO());
        stubEmptyTaskAggregates();
        // 全部为 generating：SQL 聚合排除后 total=0
        when(aiTaskMapper.selectTaskStatTotals("text", "success")).thenReturn(taskTotals(0, 0, 0, 0));
        when(aiTaskMapper.selectTaskStatRecent(eq("text"), any(), any(), any())).thenReturn(taskRecent(0, 0, 0));

        DashboardStatsVO vo = service.stats();

        assertEquals(0, vo.getTextGen().getTotal());
        assertEquals(0.0, vo.getTextGen().getSuccessRate());
        assertEquals(0, vo.getTextGen().getAvgDuration());
    }
}