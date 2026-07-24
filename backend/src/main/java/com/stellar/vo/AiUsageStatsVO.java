package com.stellar.vo;

import lombok.Data;

import java.util.List;

/**
 * AI token 消费统计（仪表盘展示）。
 */
@Data
public class AiUsageStatsVO {

    private long totalTokens;

    private long todayTokens;

    private long totalCalls;

    private long todayCalls;

    /** 近 7 日趋势 */
    private List<DailyPoint> dailyTrend;

    @Data
    public static class DailyPoint {
        private String date;
        private long tokens;
        private long calls;
    }
}
