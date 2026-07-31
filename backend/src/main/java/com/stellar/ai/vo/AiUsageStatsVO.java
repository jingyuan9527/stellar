package com.stellar.ai.vo;

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

    /** 按模型类型分组 */
    private List<TypeStat> byType;

    /** 按供应商分组 */
    private List<ProviderStat> byProvider;

    @Data
    public static class DailyPoint {
        private String date;
        private long tokens;
        private long calls;
    }

    @Data
    public static class TypeStat {
        private String modelType;
        private long tokens;
        private long calls;
    }

    @Data
    public static class ProviderStat {
        private Long providerId;
        private String providerName;
        private long tokens;
        private long calls;
    }
}
