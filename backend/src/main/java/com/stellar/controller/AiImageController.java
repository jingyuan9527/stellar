package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
import com.stellar.dto.AiImageGenerateDTO;
import com.stellar.enums.OperationType;
import com.stellar.service.AiImageService;
import com.stellar.vo.AiImageResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 图片生成接口。对游客开放，IP 日限 2 次。
 */
@Slf4j
@RestController
@RequestMapping("/ai/image")
@RequiredArgsConstructor
public class AiImageController {

    private final AiImageService aiImageService;

    @PublicAccess
    @RateLimit(daily = 2)
    @PostMapping("/generate")
    @Log(title = "AI图片生成", type = OperationType.OTHER)
    public Result<AiImageResultVO> generate(@Valid @RequestBody AiImageGenerateDTO dto) {
        return Result.success(aiImageService.generate(dto.getModelId(), dto.getPrompt(), dto.getSize()));
    }
}
