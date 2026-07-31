package com.stellar.ai.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.ai.dto.AiModelDTO;
import com.stellar.enums.OperationType;
import com.stellar.ai.service.AiModelService;
import com.stellar.ai.vo.AiModelVO;
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
 * AI 模型管理接口（需登录）。
 */
@Slf4j
@RestController
@RequestMapping("/ai/model")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService aiModelService;

    /**
     * 查全部模型（带供应商名称）。传 providerId 则只查该供应商下模型。
     */
    @GetMapping
    @Log(title = "AI模型", type = OperationType.QUERY)
    public Result<List<AiModelVO>> list(@RequestParam(required = false) Long providerId) {
        if (providerId != null) {
            return Result.success(aiModelService.listByProvider(providerId));
        }
        return Result.success(aiModelService.listAll());
    }

    /**
     * 按类型查启用的模型（供前端下拉/调用方选模型用）。对游客开放，模型名不敏感。
     */
    @PublicAccess
    @GetMapping("/type/{modelType}")
    @Log(title = "AI模型", type = OperationType.QUERY)
    public Result<List<AiModelVO>> listByType(@PathVariable String modelType) {
        return Result.success(aiModelService.listEnabledByType(modelType));
    }

    @PostMapping
    @Log(title = "AI模型", type = OperationType.INSERT)
    public Result<Void> create(@Valid @RequestBody AiModelDTO dto) {
        aiModelService.create(dto);
        return Result.success();
    }

    @PutMapping
    @Log(title = "AI模型", type = OperationType.UPDATE)
    public Result<Void> update(@Valid @RequestBody AiModelDTO dto) {
        aiModelService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(title = "AI模型", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        aiModelService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/enabled")
    @Log(title = "AI模型", type = OperationType.UPDATE)
    public Result<Void> toggleEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        aiModelService.toggleEnabled(id, enabled);
        return Result.success();
    }

    /**
     * 设为该类型默认（同类型互斥）。
     */
    @PutMapping("/{id}/default")
    @Log(title = "AI模型", type = OperationType.UPDATE)
    public Result<Void> setDefault(@PathVariable Long id) {
        aiModelService.setDefault(id);
        return Result.success();
    }
}
