package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
import com.stellar.dto.AiImageGenerateDTO;
import com.stellar.enums.OperationType;
import com.stellar.service.AiImageService;
import com.stellar.vo.AiImageTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 图片生成接口（异步任务模式）。对游客开放，IP 日限 2 次。
 * <p>POST /create 立即返回 taskId，@Async 线程生成；GET /task/{id} 轮询状态。
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
     * 查询图片任务状态。completed 时返回 /file/{id}。轮询用，不限流。
     */
    @PublicAccess
    @GetMapping("/task/{taskId}")
    @Log(title = "AI图片任务", type = OperationType.QUERY)
    public Result<AiImageTaskVO> task(@PathVariable Long taskId) {
        return Result.success(aiImageService.getTask(taskId));
    }
}
