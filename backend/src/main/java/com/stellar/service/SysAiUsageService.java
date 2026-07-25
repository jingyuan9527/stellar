package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.entity.SysAiProvider;
import com.stellar.entity.SysAiUsage;
import com.stellar.mapper.SysAiProviderMapper;
import com.stellar.mapper.SysAiUsageMapper;
import com.stellar.vo.AiUsageStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
            log.warn("[AI统计] 记录失败: {}", e.getMessage());
        }
    }

    /**
     * 统计：总 token、今日 token、调用次数、近 7 日趋势、按类型/供应商分组。
     */
    public AiUsageStatsVO stats() {
        AiUsageStatsVO vo = new AiUsageStatsVO();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 全表数据：算 total + 按类型/供应商分组
        List<SysAiUsage> allRecords = usageMapper.selectList(null);
        long totalTokens = 0;
        long totalCalls = allRecords.size();
        Map<String, long[]> typeMap = new LinkedHashMap<>();
        Map<Long, long[]> providerMap = new LinkedHashMap<>();
        for (SysAiUsage u : allRecords) {
            long t = u.getTotalTokens() == null ? 0 : u.getTotalTokens();
            totalTokens += t;
            if (u.getModelType() != null) {
                long[] arr = typeMap.computeIfAbsent(u.getModelType(), k -> new long[]{0, 0});
                arr[0] += t;
                arr[1] += 1;
            }
            if (u.getProviderId() != null) {
                long[] arr = providerMap.computeIfAbsent(u.getProviderId(), k -> new long[]{0, 0});
                arr[0] += t;
                arr[1] += 1;
            }
        }
        vo.setTotalTokens(totalTokens);
        vo.setTotalCalls(totalCalls);
        vo.setByType(buildTypeStats(typeMap));
        vo.setByProvider(buildProviderStats(providerMap));

        // 近 7 日：算 today + dailyTrend
        List<SysAiUsage> recent = usageMapper.selectList(new LambdaQueryWrapper<SysAiUsage>()
                .ge(SysAiUsage::getCreateTime, LocalDate.now().minusDays(6).atStartOfDay()));
        long todayTokens = 0;
        long todayCalls = 0;
        LinkedHashMap<String, long[]> daily = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            daily.put(LocalDate.now().minusDays(i).toString(), new long[]{0, 0});
        }
        for (SysAiUsage u : recent) {
            if (u.getCreateTime() == null) continue;
            long t = u.getTotalTokens() == null ? 0 : u.getTotalTokens();
            if (u.getCreateTime().isAfter(todayStart)) {
                todayTokens += t;
                todayCalls++;
            }
            String d = u.getCreateTime().toLocalDate().toString();
            long[] arr = daily.get(d);
            if (arr != null) {
                arr[0] += t;
                arr[1] += 1;
            }
        }
        vo.setTodayTokens(todayTokens);
        vo.setTodayCalls(todayCalls);
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

    private List<AiUsageStatsVO.TypeStat> buildTypeStats(Map<String, long[]> typeMap) {
        List<AiUsageStatsVO.TypeStat> list = new ArrayList<>();
        typeMap.forEach((type, arr) -> {
            AiUsageStatsVO.TypeStat s = new AiUsageStatsVO.TypeStat();
            s.setModelType(type);
            s.setTokens(arr[0]);
            s.setCalls(arr[1]);
            list.add(s);
        });
        return list;
    }

    private List<AiUsageStatsVO.ProviderStat> buildProviderStats(Map<Long, long[]> providerMap) {
        List<AiUsageStatsVO.ProviderStat> list = new ArrayList<>();
        Map<Long, String> nameMap = new HashMap<>();
        if (!providerMap.isEmpty()) {
            providerMapper.selectBatchIds(providerMap.keySet())
                    .forEach(p -> nameMap.put(p.getId(), p.getName()));
        }
        providerMap.forEach((pid, arr) -> {
            AiUsageStatsVO.ProviderStat s = new AiUsageStatsVO.ProviderStat();
            s.setProviderId(pid);
            s.setProviderName(nameMap.get(pid));
            s.setTokens(arr[0]);
            s.setCalls(arr[1]);
            list.add(s);
        });
        return list;
    }
}
