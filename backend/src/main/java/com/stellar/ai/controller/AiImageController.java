package com.stellar.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
import com.stellar.ai.dto.AiImageGenerateDTO;
import com.stellar.ai.dto.AiImageHistoryQueryDTO;
import com.stellar.enums.OperationType;
import com.stellar.ai.service.AiImageService;
import com.stellar.ai.vo.AiImageTaskVO;
import com.stellar.interceptor.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 图片生成接口（异步任务模式）。对游客开放，IP 日限 2 次。
 * <p>POST /create 立即返回 taskId，@Async 线程生成；GET /task/{id} 查询状态（兜底，前端改用 SSE 通知）；
 * GET /page 按主体（account/ip）分页查看历史。
 */
@Slf4j
@RestController
@RequestMapping("/ai/image")
@RequiredArgsConstructor
public class AiImageController {

    private final AiImageService aiImageService;

    @PublicAccess
    @RateLimit(daily = 2)
    @PostMapping("/create")
    @Log(title = "AI图片生成", type = OperationType.OTHER)
    public Result<Long> create(@Valid @RequestBody AiImageGenerateDTO dto) {
        return Result.success(aiImageService.createTask(dto.getModelId(), dto.getPrompt(), dto.getSize(), dto.getRatio()));
    }

    /**
     * 查询图片任务状态。completed 时返回 /file/{id}。状态查询用，不限流（前端改用 SSE 通知，此接口保留兜底）。
     */
    @PublicAccess
    @GetMapping("/task/{taskId}")
    @Log(title = "AI图片任务", type = OperationType.QUERY)
    public Result<AiImageTaskVO> task(@PathVariable Long taskId) {
        return Result.success(aiImageService.getTask(taskId));
    }

    /**
     * 分页查询当前主体的图片生成历史（登录按账号、游客按 IP）。
     */
    @PublicAccess
    @GetMapping("/page")
    @Log(title = "AI图片历史", type = OperationType.QUERY)
    public Result<Page<AiImageTaskVO>> page(@Valid @ModelAttribute AiImageHistoryQueryDTO query, HttpServletRequest request) {
        String subjectType = StpUtil.isLogin() ? "account" : "ip";
        String subjectId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : WebUtils.getClientIp(request);
        return Result.success(aiImageService.pageHistory(query, subjectType, subjectId));
    }

    /**
     * 删除图片生成历史（连关联文件一起删，校验归属）。
     */
    @PublicAccess
    @DeleteMapping("/{taskId}")
    @Log(title = "AI图片历史", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long taskId, HttpServletRequest request) {
        String subjectType = StpUtil.isLogin() ? "account" : "ip";
        String subjectId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : WebUtils.getClientIp(request);
        aiImageService.deleteTask(taskId, subjectType, subjectId);
        return Result.success();
    }
}

