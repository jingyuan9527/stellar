package com.stellar.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.BusinessException;
import com.stellar.common.Result;
import com.stellar.enums.OperationType;
import com.stellar.ai.service.AiMemoryService;
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

import java.util.Map;

/**
 * AI 长期记忆管理：分页查看/编辑/删除 + 手动触发某会话整理。
 */
@Slf4j
@RestController
@RequestMapping("/ai/memory")
@RequiredArgsConstructor
public class AiMemoryController {

    private final AiMemoryService memoryService;

    @GetMapping
    @Log(title = "长期记忆", type = OperationType.QUERY)
    public Result<Page<Map<String, Object>>> pageAll(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(memoryService.pageAll(pageNum, pageSize));
    }

    @GetMapping("/user/{userId}")
    @Log(title = "长期记忆", type = OperationType.QUERY)
    public Result<Page<Map<String, Object>>> pageByUser(@PathVariable Long userId,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(memoryService.pageByUser(userId, pageNum, pageSize));
    }

    @PutMapping("/{id}")
    @Log(title = "长期记忆", type = OperationType.UPDATE)
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        memoryService.update(id, body.get("content"));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(title = "长期记忆", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        memoryService.delete(id);
        return Result.success();
    }

    /**
     * 手动新增长期记忆（管理员指定用户与内容）。
     */
    @PostMapping
    @Log(title = "长期记忆", type = OperationType.INSERT)
    public Result<Void> create(@RequestBody Map<String, Object> body) {
        Object uid = body.get("userId");
        if (!(uid instanceof Number)) {
            throw new BusinessException("userId 不能为空");
        }
        memoryService.create(((Number) uid).longValue(), (String) body.get("content"));
        return Result.success();
    }

    /**
     * 手动触发某会话整理为长期记忆（定时任务每日 3 点自动跑，此为手动补跑）。
     */
    @PostMapping("/summarize/{sessionId}")
    @Log(title = "长期记忆", type = OperationType.OTHER)
    public Result<Integer> summarizeSession(@PathVariable Long sessionId) {
        return Result.success(memoryService.summarizeSession(sessionId));
    }
}
