package com.stellar.system.vo;

import lombok.Data;

import java.util.List;
import com.stellar.ai.vo.AiUsageStatsVO;

/**
 * 仪表盘聚合统计：AI 调用 token + 各模块任务/文件/TTS 概览。
 * <p>聚合自多张表（sys_ai_usage/ai_task/sys_file），其中文案/图片/视频/TTS 任务均读统一 ai_task 表
 * （task_type=text/image/video/tts），只读查询，供管理后台仪表盘展示。
 */
@Data
public class DashboardStatsVO {

    /** AI token 调用统计（总/今日 token、调用次数、近 7 日趋势、按类型/供应商分组） */
    private AiUsageStatsVO aiUsage;

    /** AI 文案任务统计（ai_task task_type=text） */
    private TaskStat textGen;

    /** AI 图片任务统计（ai_task task_type=image） */
    private TaskStat imageTask;

    /** AI 视频任务统计（ai_task task_type=video） */
    private TaskStat videoTask;

    /** 文件统计（sys_file） */
    private FileStat file;

    /** TTS 合成统计（ai_task task_type=tts） */
    private TtsStat tts;

    /**
     * 通用任务统计：总数、今日数、成功数、成功率(%)、平均耗时。
     * <p>文案记录耗时单位为毫秒，图片/视频任务耗时单位为秒（由 updateTime-createTime 估算）。
     */
    @Data
    public static class TaskStat {
        /** 终态任务总数（排除进行中） */
        private long total;
        private long today;
        private long successCount;
        /** 成功率 0-100，保留 1 位小数 */
        private double successRate;
        /** 平均耗时（文案 ms / 图片视频 s），仅统计终态任务 */
        private long avgDuration;
    }

    @Data
    public static class FileStat {
        private long total;
        private long todayUpload;
        /** 总占用字节 */
        private long totalSize;
        /** 按文件类型分组（image/audio/other） */
        private List<FileTypeStat> byType;
    }

    @Data
    public static class FileTypeStat {
        /** image / audio / other */
        private String type;
        private long count;
        private long size;
    }

    @Data
    public static class TtsStat {
        private long total;
        private long today;
        /** 总音频字节 */
        private long totalSize;
    }
}
