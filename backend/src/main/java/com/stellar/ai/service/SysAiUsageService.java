package com.stellar.ai.service;

import com.stellar.ai.entity.SysAiProvider;
import com.stellar.ai.entity.SysAiUsage;
import com.stellar.ai.mapper.SysAiProviderMapper;
import com.stellar.ai.mapper.SysAiUsageMapper;
import com.stellar.ai.vo.AiUsageStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI token 消费记录与统计服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiUsageService {

    private final SysAiUsageMapper usageMapper;
    private final SysAiProviderMapper providerMapper;

    /** 记录一次 AI 调用的 token 消耗（失败不影响主流程） */
    public void record(String subjectType, String subjectId, Long providerId, String model, String modelType,
                       Integer promptTokens, Integer completionTokens, Integer totalTokens, String source) {
        try {
            SysAiUsage u = new SysAiUsage();
            u.setSubjectType(subjectType);
            u.setSubjectId(subjectId);
            u.setProviderId(providerId);
            u.setModel(model);
            u.setModelType(modelType);
            u.setPromptTokens(promptTokens == null ? 0 : promptTokens);
            u.setCompletionTokens(completionTokens == null ? 0 : completionTokens);
            u.setTotalTokens(totalTokens == null ? 0 : totalTokens);
            u.setSource(source);
            u.setCreateTime(LocalDateTime.now());
            usageMapper.insert(u);
        } catch (Exception e) {
            log.warn("[AI统计] 记录失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 统计：总 token、今日 token、调用次数、近 7 日趋势、按类型/供应商分组。
     * <p>全部走 SQL 聚合，避免全表加载到内存（线上 token 记录会到百万级）。
     */
    public AiUsageStatsVO stats() {
        AiUsageStatsVO vo = new AiUsageStatsVO();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();

        // 总量 + 今日（单次 SQL 聚合）
        Map<String, Object> totals = usageMapper.selectTotals(todayStart);
        if (totals != null) {
            vo.setTotalTokens(toLong(totals.get("total_tokens")));
            vo.setTotalCalls(toLong(totals.get("total_calls")));
            vo.setTodayTokens(toLong(totals.get("today_tokens")));
            vo.setTodayCalls(toLong(totals.get("today_calls")));
        }

        // 按类型分组
        List<AiUsageStatsVO.TypeStat> typeStats = new ArrayList<>();
        for (Map<String, Object> row : usageMapper.selectByType()) {
            AiUsageStatsVO.TypeStat s = new AiUsageStatsVO.TypeStat();
            s.setModelType((String) row.get("model_type"));
            s.setTokens(toLong(row.get("tokens")));
            s.setCalls(toLong(row.get("calls")));
            typeStats.add(s);
        }
        vo.setByType(typeStats);

        // 按供应商分组（聚合后批量查供应商名）
        List<Map<String, Object>> providerRows = usageMapper.selectByProvider();
        Map<Long, String> nameMap = new HashMap<>();
        if (!providerRows.isEmpty()) {
            Set<Long> providerIds = new HashSet<>();
            for (Map<String, Object> row : providerRows) {
                Long pid = toLongOrNull(row.get("provider_id"));
                if (pid != null) providerIds.add(pid);
            }
            if (!providerIds.isEmpty()) {
                providerMapper.selectBatchIds(providerIds)
                        .forEach(p -> nameMap.put(p.getId(), p.getName()));
            }
        }
        List<AiUsageStatsVO.ProviderStat> providerStats = new ArrayList<>();
        for (Map<String, Object> row : providerRows) {
            AiUsageStatsVO.ProviderStat s = new AiUsageStatsVO.ProviderStat();
            Long pid = toLongOrNull(row.get("provider_id"));
            s.setProviderId(pid);
            s.setProviderName(nameMap.get(pid));
            s.setTokens(toLong(row.get("tokens")));
            s.setCalls(toLong(row.get("calls")));
            providerStats.add(s);
        }
        vo.setByProvider(providerStats);

        // 近 7 日趋势（DB 聚合 + Java 补空白天）
        LinkedHashMap<String, long[]> daily = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            daily.put(LocalDate.now().minusDays(i).toString(), new long[]{0, 0});
        }
        for (Map<String, Object> row : usageMapper.selectDailyTrend(weekStart)) {
            Object dObj = row.get("d");
            if (dObj == null) continue;
            String d = dObj.toString();
            long[] arr = daily.get(d);
            if (arr != null) {
                arr[0] = toLong(row.get("tokens"));
                arr[1] = toLong(row.get("calls"));
            }
        }
        List<AiUsageStatsVO.DailyPoint> trend = new ArrayList<>();
        daily.forEach((d, arr) -> {
            AiUsageStatsVO.DailyPoint p = new AiUsageStatsVO.DailyPoint();
            p.setDate(d);
            p.setTokens(arr[0]);
            p.setCalls(arr[1]);
            trend.add(p);
        });
        vo.setDailyTrend(trend);
        return vo;
    }

    private long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0; }
    }

    private Long toLongOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }
}

