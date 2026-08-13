package com.stellar.memos.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.enums.OperationType;
import com.stellar.memos.service.MemosRagService;
import com.stellar.memos.vo.MemosJobResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 备忘笔记 RAG 管理接口（需登录）：
 * 全量重建向量索引（新增 embedding 不触发向量化或模型变更后的兜底）；查询索引构建状态。
 */
@Slf4j
@RestController
@RequestMapping("/memos/rag")
@RequiredArgsConstructor
public class MemosRagController {

    private final MemosRagService memosRagService;

    @PostMapping("/rebuild")
    @Log(title = "备忘RAG", type = OperationType.UPDATE)
    public Result<MemosJobResultVO> rebuild() {
        return Result.success(memosRagService.rebuildAll());
    }

    @GetMapping("/status")
    @Log(title = "备忘RAG", type = OperationType.QUERY)
    public Result<Map<String, Object>> status() {
        return Result.success(memosRagService.status());
    }
}
