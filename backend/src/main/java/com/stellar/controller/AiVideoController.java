package com.stellar.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.RateLimit;
import com.stellar.dto.AiVideoCreateDTO;
import com.stellar.dto.AiVideoHistoryQueryDTO;
import com.stellar.enums.OperationType;
import com.stellar.service.AiVideoService;
import com.stellar.vo.AiVideoHistoryVO;
import com.stellar.vo.AiVideoStatusVO;
import com.stellar.vo.AiVideoTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 视频生成接口（需登录）。异步任务：创建任务 → 轮询状态 → 历史分页。
 */
@Slf4j
@RestController
@RequestMapping("/ai/video")
@RequiredArgsConstructor
public class AiVideoController {

    private final AiVideoService aiVideoService;

    /**
     * 创建视频生成任务，返回 video_id 供前端轮询。日限 3 次。同时本地留痕。
     */
    @RateLimit(daily = 3)
    @PostMapping("/create")
    @Log(title = "AI视频生成", type = OperationType.OTHER)
    public Result<AiVideoTaskVO> create(@Valid @RequestBody AiVideoCreateDTO dto) {
        return Result.success(aiVideoService.createTask(dto));
    }

    /**
     * 查询视频任务状态。completed 时返回 /file/{id}。轮询用，不限流。
     */
    @GetMapping("/status")
    @Log(title = "AI视频状态", type = OperationType.QUERY)
    public Result<AiVideoStatusVO> status(@RequestParam Long modelId,
                                          @RequestParam String videoId) {
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
}
