package com.stellar.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.dto.AiCopyResultQueryDTO;
import com.stellar.dto.AiCopyResultSaveDTO;
import com.stellar.entity.SysAiCopyResult;
import com.stellar.enums.OperationType;
import com.stellar.service.AiCopyResultService;
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

@Slf4j
@RestController
@RequestMapping("/ai/copy-result")
@RequiredArgsConstructor
public class AiCopyResultController {

    private final AiCopyResultService aiCopyResultService;

    @GetMapping("/page")
    @Log(title = "文案历史", type = OperationType.QUERY)
    public Result<Page<SysAiCopyResult>> page(@ModelAttribute AiCopyResultQueryDTO query) {
        return Result.success(aiCopyResultService.page(query, StpUtil.getLoginIdAsLong()));
    }

    @PostMapping
    @Log(title = "文案历史", type = OperationType.INSERT)
    public Result<Void> save(@Valid @RequestBody AiCopyResultSaveDTO dto) {
        aiCopyResultService.save(dto, StpUtil.getLoginIdAsLong());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(title = "文案历史", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        aiCopyResultService.delete(id, StpUtil.getLoginIdAsLong());
        return Result.success();
    }

    @DeleteMapping
    @Log(title = "文案历史", type = OperationType.DELETE)
    public Result<Void> clear() {
        aiCopyResultService.clear(StpUtil.getLoginIdAsLong());
        return Result.success();
    }
}
