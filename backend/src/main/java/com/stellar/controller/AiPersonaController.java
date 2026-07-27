package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.dto.AiPersonaDTO;
import com.stellar.entity.AiPersona;
import com.stellar.enums.OperationType;
import com.stellar.service.AiPersonaService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 人设管理：公开查启用列表（聊天页下拉），登录 CRUD。
 */
@Slf4j
@RestController
@RequestMapping("/ai/persona")
@RequiredArgsConstructor
public class AiPersonaController {

    private final AiPersonaService aiPersonaService;

    /**
     * 查启用的人设列表（聊天页下拉用，对游客公开）。
     */
    @PublicAccess
    @GetMapping("/enabled")
    @Log(title = "AI人设", type = OperationType.QUERY)
    public Result<List<AiPersona>> listEnabled() {
        return Result.success(aiPersonaService.listEnabled());
    }

    /**
     * 管理后台查全部（含禁用，需登录）。
     */
    @GetMapping
    @Log(title = "AI人设", type = OperationType.QUERY)
    public Result<List<AiPersona>> listAll() {
        return Result.success(aiPersonaService.listAll());
    }

    @PostMapping
    @Log(title = "AI人设", type = OperationType.INSERT)
    public Result<Void> create(@Valid @RequestBody AiPersonaDTO dto) {
        aiPersonaService.create(dto);
        return Result.success();
    }

    @PutMapping
    @Log(title = "AI人设", type = OperationType.UPDATE)
    public Result<Void> update(@Valid @RequestBody AiPersonaDTO dto) {
        aiPersonaService.update(dto);
        return Result.success();
    }

    @PutMapping("/{id}/enabled")
    @Log(title = "AI人设", type = OperationType.UPDATE)
    public Result<Void> toggleEnabled(@PathVariable Long id, Integer enabled) {
        aiPersonaService.toggleEnabled(id, enabled);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(title = "AI人设", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        aiPersonaService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/reset")
    @Log(title = "AI人设", type = OperationType.UPDATE)
    public Result<Void> resetBuiltin(@PathVariable Long id) {
        aiPersonaService.resetBuiltin(id);
        return Result.success();
    }
}
