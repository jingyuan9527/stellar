package com.stellar.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.RateLimit;
import com.stellar.ai.dto.AiVideoCreateDTO;
import com.stellar.ai.dto.AiVideoHistoryQueryDTO;
import com.stellar.enums.OperationType;
import com.stellar.ai.service.AiVideoService;
import com.stellar.ai.vo.AiVideoHistoryVO;
import com.stellar.ai.vo.AiVideoStatusVO;
import com.stellar.ai.vo.AiVideoTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 视频生成接口（需登录）。异步任务：创建任务 → 后端 worker 轮询 → SSE 通知 → 历史分页。
 */
@Slf4j
@RestController
@RequestMapping("/ai/video")
@RequiredArgsConstructor
public class AiVideoController {

    private final AiVideoService aiVideoService;

    /**
     * 创建视频生成任务，返回 video_id 供后端 worker 轮询。日限 3 次。同时本地留痕。
     */
    @RateLimit(daily = 3)
    @PostMapping("/create")
    @Log(title = "AI视频生成", type = OperationType.OTHER)
    public Result<AiVideoTaskVO> create(@Valid @RequestBody AiVideoCreateDTO dto) {
        return Result.success(aiVideoService.createTask(dto));
    }

    /**
     * 查询视频任务状态。completed 时返回 /file/{id}。状态查询用，不限流（前端改用 SSE 通知，此接口保留兜底）。
     */
    @GetMapping("/status")
    @Log(title = "AI视频状态", type = OperationType.QUERY)
    public Result<AiVideoStatusVO> status(@RequestParam Long modelId,
                                          @RequestParam String videoId) {
        String subjectId = StpUtil.getLoginIdAsString();
        // 防越权：videoId 为全局标识，先校验归属再查询（详见 AiVideoService.assertVideoOwner）
        aiVideoService.assertVideoOwner(videoId, "account", subjectId);
        return Result.success(aiVideoService.getTask(modelId, videoId));
    }

    /**
     * 分页查询当前用户的视频生成历史（登录按账号）。
     */
    @GetMapping("/page")
    @Log(title = "AI视频历史", type = OperationType.QUERY)
    public Result<Page<AiVideoHistoryVO>> page(@ModelAttribute AiVideoHistoryQueryDTO query) {
        String subjectId = StpUtil.getLoginIdAsString();
        return Result.success(aiVideoService.pageHistory(query, "account", subjectId));
    }

    /**
     * 删除视频生成历史（连关联文件一起删，校验归属）。
     */
    @DeleteMapping("/{taskId}")
    @Log(title = "AI视频历史", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long taskId) {
        String subjectId = StpUtil.getLoginIdAsString();
        aiVideoService.deleteTask(taskId, "account", subjectId);
        return Result.success();
    }
}
