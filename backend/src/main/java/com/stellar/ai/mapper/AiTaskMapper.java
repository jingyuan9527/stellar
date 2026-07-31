package com.stellar.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stellar.ai.entity.AiTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AiTaskMapper extends BaseMapper<AiTask> {

    @Select("SELECT * FROM ai_task "
            + "WHERE task_type = 'video' AND deleted = 0 "
            + "AND extra ->> 'video_id' = #{videoId} "
            + "ORDER BY id DESC LIMIT 1")
    AiTask selectVideoTaskByVideoId(@Param("videoId") String videoId);
}
