package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.dto.AiProviderDTO;
import com.stellar.enums.OperationType;
import com.stellar.service.AiProviderService;
import com.stellar.vo.AiProviderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 供应商管理接口（需登录）。
 */
@Slf4j
@RestController
@RequestMapping("/ai/provider")
@RequiredArgsConstructor
public class AiProviderController {

    private final AiProviderService aiProviderService;

    @GetMapping
    @Log(title = "AI供应商", type = OperationType.QUERY)
    public Result<List<AiProviderVO>> list() {
        return Result.success(aiProviderService.list());
    }

    @PostMapping
    @Log(title = "AI供应商", type = OperationType.INSERT)
    public Result<Void> create(@Valid @RequestBody AiProviderDTO dto) {
        aiProviderService.create(dto);
        return Result.success();
    }

    @PutMapping
    @Log(title = "AI供应商", type = OperationType.UPDATE)
    public Result<Void> update(@Valid @RequestBody AiProviderDTO dto) {
        aiProviderService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(title = "AI供应商", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        aiProviderService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/enabled")
    @Log(title = "AI供应商", type = OperationType.UPDATE)
    public Result<Void> toggleEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        aiProviderService.toggleEnabled(id, enabled);
        return Result.success();
    }

    /**
     * 拉取供应商端点支持的模型列表，落库 available_models。
     */
    @GetMapping("/{id}/models")
    @Log(title = "AI供应商", type = OperationType.QUERY)
    public Result<List<String>> fetchModels(@PathVariable Long id) {
        return Result.success(aiProviderService.fetchModels(id));
    }

    /**
     * 测试供应商连通性。model 可选，未传则取该供应商下任一启用模型。
     */
    @GetMapping("/{id}/test")
    @Log(title = "AI供应商", type = OperationType.OTHER)
    public Result<Void> test(@PathVariable Long id,
                              @RequestParam(required = false) String model) {
        aiProviderService.testConnection(id, model);
        return Result.success();
    }
}
