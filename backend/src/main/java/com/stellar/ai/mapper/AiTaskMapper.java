package com.stellar.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stellar.ai.entity.AiTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

public interface AiTaskMapper extends BaseMapper<AiTask> {

    @Select("SELECT * FROM ai_task "
            + "WHERE task_type = 'video' AND deleted = 0 "
            + "AND extra ->> 'video_id' = #{videoId} "
            + "ORDER BY id DESC LIMIT 1")
    AiTask selectVideoTaskByVideoId(@Param("videoId") String videoId);

    /**
     * 任务全量聚合（排除进行中/null 状态）：总数 / 成功数 / 成功任务耗时和 / 成功任务耗时条数。
     * <p>聚合下推 SQL，避免仪表盘把整张 ai_task 拉进 JVM 遍历（随调用量无限增长）。
     */
    @Select("SELECT COUNT(*) AS total, " +
            "COUNT(*) FILTER (WHERE status = #{successStatus}) AS success, " +
            "COALESCE(SUM(duration_ms) FILTER (WHERE status = #{successStatus} AND duration_ms > 0), 0) AS duration_sum, " +
            "COUNT(duration_ms) FILTER (WHERE status = #{successStatus} AND duration_ms > 0) AS duration_count " +
            "FROM ai_task " +
            "WHERE deleted = 0 AND task_type = #{taskType} " +
            "AND status IS NOT NULL AND status <> 'generating'")
    Map<String, Object> selectTaskStatTotals(@Param("taskType") String taskType,
                                             @Param("successStatus") String successStatus);

    /**
     * 任务近 13 日时间桶聚合（今日 / 近 7 日 / 前 7 日），按 request_time 范围过滤命中 idx_ai_task_request_time。
     */
    @Select("SELECT " +
            "COUNT(*) FILTER (WHERE request_time >= #{todayStart}) AS today, " +
            "COUNT(*) FILTER (WHERE request_time >= #{weekStart}) AS week_total, " +
            "COUNT(*) FILTER (WHERE request_time >= #{prevWeekStart} AND request_time < #{weekStart}) AS prev_week_total " +
            "FROM ai_task " +
            "WHERE deleted = 0 AND task_type = #{taskType} " +
            "AND status IS NOT NULL AND status <> 'generating' " +
            "AND request_time >= #{prevWeekStart}")
    Map<String, Object> selectTaskStatRecent(@Param("taskType") String taskType,
                                             @Param("todayStart") LocalDateTime todayStart,
                                             @Param("weekStart") LocalDateTime weekStart,
                                             @Param("prevWeekStart") LocalDateTime prevWeekStart);

    /** TTS 全量聚合：总数 / 今日 / 总字节，避免把全部 tts 记录拉进 JVM。 */
    @Select("SELECT COUNT(*) AS total, " +
            "COUNT(*) FILTER (WHERE request_time >= #{todayStart}) AS today, " +
            "COALESCE(SUM(file_size), 0) AS total_size " +
            "FROM ai_task " +
            "WHERE deleted = 0 AND task_type = 'tts'")
    Map<String, Object> selectTtsStat(@Param("todayStart") LocalDateTime todayStart);
}
