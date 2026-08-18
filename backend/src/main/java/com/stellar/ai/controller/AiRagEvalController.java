package com.stellar.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.ai.dto.RagEvalCaseDTO;
import com.stellar.ai.entity.RagEvalCase;
import com.stellar.ai.entity.RagEvalResult;
import com.stellar.ai.service.RagEvalService;
import com.stellar.ai.vo.RagEvalRunVO;
import com.stellar.ai.vo.RagFeedbackVO;
import com.stellar.enums.OperationType;
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
 * RAG 数据飞轮（需登录）：评估集 CRUD（golden set）、离线跑分（纯检索 recall@k）、
 * 反馈复盘分页（bad case 原料）。跑分不改写/不重排/不调 LLM，纯检索路径。
 */
@Slf4j
@RestController
@RequestMapping("/ai/rag/eval")
@RequiredArgsConstructor
public class AiRagEvalController {

    private final RagEvalService ragEvalService;

    @GetMapping("/case")
    @Log(title = "RAG评估", type = OperationType.QUERY)
    public Result<Page<RagEvalCase>> pageCases(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(ragEvalService.pageCases(pageNum, pageSize));
    }

    @PostMapping("/case")
    @Log(title = "RAG评估", type = OperationType.INSERT)
    public Result<Void> createCase(@Valid @RequestBody RagEvalCaseDTO dto) {
        ragEvalService.createCase(dto);
        return Result.success();
    }

    @PutMapping("/case")
    @Log(title = "RAG评估", type = OperationType.UPDATE)
    public Result<Void> updateCase(@Valid @RequestBody RagEvalCaseDTO dto) {
        ragEvalService.updateCase(dto);
        return Result.success();
    }

    @DeleteMapping("/case/{id}")
    @Log(title = "RAG评估", type = OperationType.DELETE)
    public Result<Void> deleteCase(@PathVariable Long id) {
        ragEvalService.deleteCase(id);
        return Result.success();
    }

    /**
     * 跑分：默认纯检索路径（不调 LLM，秒级），mode=full 走完整管线（含改写/重排/loop，与线上一致，较慢）。
     * 返回本次 run 汇总 + 明细。
     */
    @PostMapping("/run")
    @Log(title = "RAG评估", type = OperationType.OTHER)
    public Result<RagEvalRunVO> run(@RequestParam(defaultValue = "retrieval") String mode) {
        return Result.success(ragEvalService.runEvaluation(mode));
    }

    /** 某次跑分批次的全部结果（按 runId 回看/回归对比）。 */
    @GetMapping("/run/{runId}")
    @Log(title = "RAG评估", type = OperationType.QUERY)
    public Result<List<RagEvalResult>> runResults(@PathVariable String runId) {
        return Result.success(ragEvalService.getRunResults(runId));
    }

    /** 最近的跑分批次列表（回看历史跑分用）。 */
    @GetMapping("/run")
    @Log(title = "RAG评估", type = OperationType.QUERY)
    public Result<List<String>> recentRuns(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(ragEvalService.listRecentRuns(limit));
    }

    /**
     * 反馈复盘分页（bad case 原料）：value 过滤（-1=坏样本/1=好样本/空=全部），
     * 联查消息 content 与 RAG 引用。
     */
    @GetMapping("/feedback")
    @Log(title = "RAG评估", type = OperationType.QUERY)
    public Result<Page<RagFeedbackVO>> feedback(
            @RequestParam(required = false) Integer value,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(ragEvalService.pageFeedback(value, pageNum, pageSize));
    }
}