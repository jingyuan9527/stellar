package com.stellar.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.ai.dto.AiTemplateDTO;
import com.stellar.ai.dto.AiTemplateQueryDTO;
import com.stellar.ai.entity.SysAiTemplate;
import com.stellar.enums.OperationType;
import com.stellar.ai.service.AiTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/ai/template")
@RequiredArgsConstructor
public class AiTemplateController {

    private final AiTemplateService aiTemplateService;

    @PublicAccess
    @GetMapping("/page")
    @Log(title = "AI模板", type = OperationType.QUERY)
    public Result<Page<SysAiTemplate>> page(@ModelAttribute AiTemplateQueryDTO query) {
        return Result.success(aiTemplateService.page(query));
    }

    @PostMapping
    @Log(title = "AI模板", type = OperationType.INSERT)
    public Result<Void> create(@Valid @RequestBody AiTemplateDTO dto) {
        aiTemplateService.create(dto, StpUtil.getLoginIdAsLong());
        return Result.success();
    }

    @PutMapping("/{id}")
    @Log(title = "AI模板", type = OperationType.UPDATE)
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AiTemplateDTO dto) {
        aiTemplateService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(title = "AI模板", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        aiTemplateService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/reset")
    @Log(title = "AI模板", type = OperationType.UPDATE)
    public Result<Void> resetBuiltin(@PathVariable Long id) {
        aiTemplateService.resetBuiltin(id);
        return Result.success();
    }
}
