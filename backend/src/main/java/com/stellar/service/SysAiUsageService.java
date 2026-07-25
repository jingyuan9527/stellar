package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.entity.SysAiUsage;
import com.stellar.mapper.SysAiUsageMapper;
import com.stellar.vo.AiUsageStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * AI token 消费记录与统计服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysAiUsageService {

    private final SysAiUsageMapper usageMapper;

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
     * 统计：总 token、今日 token、调用次数、近 7 日趋势。
     */
    public AiUsageStatsVO stats() {
        AiUsageStatsVO vo = new AiUsageStatsVO();
        List<SysAiUsage> all = usageMapper.selectList(new LambdaQueryWrapper<SysAiUsage>()
                .ge(SysAiUsage::getCreateTime, LocalDate.now().minusDays(6).atStartOfDay()));
        long totalTokens = 0;
        long todayTokens = 0;
        long totalCalls = all.size();
        long todayCalls = 0;
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LinkedHashMap<String, long[]> daily = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            daily.put(LocalDate.now().minusDays(i).toString(), new long[]{0, 0});
        }
        // total 需全表，单独查
        Long allTotal = usageMapper.selectList(null).stream()
                .mapToLong(u -> u.getTotalTokens() == null ? 0 : u.getTotalTokens()).sum();
        Long allCalls = usageMapper.selectCount(null);
        for (SysAiUsage u : all) {
            if (u.getCreateTime() == null) continue;
            long t = u.getTotalTokens() == null ? 0 : u.getTotalTokens();
            totalTokens += t;
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
        vo.setTotalTokens(allTotal);
        vo.setTotalCalls(allCalls);
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
}
