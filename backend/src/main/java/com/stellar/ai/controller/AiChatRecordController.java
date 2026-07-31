package com.stellar.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.service.AiTaskService;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.enums.OperationType;
import com.stellar.infra.SubjectUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/ai/chat/record")
@RequiredArgsConstructor
public class AiChatRecordController {

    private final AiTaskService aiTaskService;

    @PublicAccess
    @GetMapping("/page")
    @Log(title = "文本生成历史", type = OperationType.QUERY)
    public Result<Page<AiTask>> page(@RequestParam(defaultValue = "1") int pageNum,
                                     @RequestParam(defaultValue = "10") int pageSize,
                                     HttpServletRequest request) {
        String subjectType = SubjectUtils.subjectType();
        String subjectId = SubjectUtils.subjectId(request);
        return Result.success(aiTaskService.page("text", subjectType, subjectId, pageNum, pageSize));
    }

    @PublicAccess
    @DeleteMapping("/{id}")
    @Log(title = "文本生成历史", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String subjectType = SubjectUtils.subjectType();
        String subjectId = SubjectUtils.subjectId(request);
        aiTaskService.delete(id, subjectType, subjectId);
        return Result.success();
    }

    @PublicAccess
    @DeleteMapping
    @Log(title = "文本生成历史", type = OperationType.DELETE)
    public Result<Void> clear(HttpServletRequest request) {
        String subjectType = SubjectUtils.subjectType();
        String subjectId = SubjectUtils.subjectId(request);
        aiTaskService.clear("text", subjectType, subjectId);
        return Result.success();
    }
}
