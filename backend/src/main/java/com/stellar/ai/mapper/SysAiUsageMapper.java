package com.stellar.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stellar.ai.entity.SysAiUsage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SysAiUsageMapper extends BaseMapper<SysAiUsage> {

    @Select("SELECT COALESCE(SUM(total_tokens), 0) AS total_tokens, COUNT(*) AS total_calls, " +
            "COALESCE(SUM(CASE WHEN create_time >= #{todayStart} THEN total_tokens ELSE 0 END), 0) AS today_tokens, " +
            "COALESCE(SUM(CASE WHEN create_time >= #{todayStart} THEN 1 ELSE 0 END), 0) AS today_calls " +
            "FROM sys_ai_usage")
    Map<String, Object> selectTotals(@Param("todayStart") LocalDateTime todayStart);

    @Select("SELECT model_type, COALESCE(SUM(total_tokens), 0) AS tokens, COUNT(*) AS calls " +
            "FROM sys_ai_usage WHERE model_type IS NOT NULL GROUP BY model_type")
    List<Map<String, Object>> selectByType();

    @Select("SELECT provider_id, COALESCE(SUM(total_tokens), 0) AS tokens, COUNT(*) AS calls " +
            "FROM sys_ai_usage WHERE provider_id IS NOT NULL GROUP BY provider_id")
    List<Map<String, Object>> selectByProvider();

    @Select("SELECT TO_CHAR(create_time, 'YYYY-MM-DD') AS d, COALESCE(SUM(total_tokens), 0) AS tokens, COUNT(*) AS calls " +
            "FROM sys_ai_usage WHERE create_time >= #{weekStart} GROUP BY TO_CHAR(create_time, 'YYYY-MM-DD')")
    List<Map<String, Object>> selectDailyTrend(@Param("weekStart") LocalDateTime weekStart);
}
