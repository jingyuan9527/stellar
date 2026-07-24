package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.dto.AiConfigDTO;
import com.stellar.enums.OperationType;
import com.stellar.service.AiConfigService;
import com.stellar.vo.AiConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai/config")
@RequiredArgsConstructor
public class AiConfigController {

    private final AiConfigService aiConfigService;

    @GetMapping
    @Log(title = "AI配置", type = OperationType.QUERY)
    public Result<AiConfigVO> get() {
        return Result.success(aiConfigService.getConfig());
    }

    @PutMapping
    @Log(title = "AI配置", type = OperationType.UPDATE)
    public Result<Void> update(@RequestBody AiConfigDTO dto) {
        aiConfigService.updateConfig(dto);
        return Result.success();
    }

    @GetMapping("/models")
    @Log(title = "AI配置", type = OperationType.QUERY)
    public Result<List<String>> fetchModels(
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String apiKey) {
        return Result.success(aiConfigService.fetchModels(endpoint, apiKey));
    }

    @GetMapping("/test")
    @Log(title = "AI配置", type = OperationType.OTHER)
    public Result<Void> test(
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String apiKey,
            @RequestParam(required = false) String model) {
        aiConfigService.testConnection(endpoint, apiKey, model);
        return Result.success();
    }
}
