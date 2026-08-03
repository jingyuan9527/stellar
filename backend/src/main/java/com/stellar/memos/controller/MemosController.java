package com.stellar.memos.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.enums.OperationType;
import com.stellar.memos.dto.MemosConfigDTO;
import com.stellar.memos.dto.MemosQueryDTO;
import com.stellar.memos.dto.MemosTagDTO;
import com.stellar.memos.service.MemosService;
import com.stellar.memos.vo.MemosConfigVO;
import com.stellar.memos.vo.MemosJobResultVO;
import com.stellar.memos.vo.MemosNoteVO;
import com.stellar.memos.vo.MemosStatsVO;
import com.stellar.memos.vo.MemosSyncResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 备忘同步接口（需登录）：
 * 配置（域名/Token/提示词模板）、立即同步（拉取备份+标记远端已删）、
 * AI 打标签、标签写回 Memos、笔记分页与统计。
 * <p>同步/打标签/写回均为同步耗时操作，前端调用时加大超时。
 */
@Slf4j
@RestController
@RequestMapping("/memos")
@RequiredArgsConstructor
public class MemosController {

    private final MemosService memosService;

    @GetMapping("/config")
    @Log(title = "备忘同步", type = OperationType.QUERY)
    public Result<MemosConfigVO> getConfig() {
        return Result.success(memosService.getConfig());
    }

    @PutMapping("/config")
    @Log(title = "备忘同步", type = OperationType.UPDATE)
    public Result<Void> saveConfig(@RequestBody MemosConfigDTO dto) {
        memosService.saveConfig(dto);
        return Result.success();
    }

    @PostMapping("/pull")
    @Log(title = "备忘同步", type = OperationType.OTHER)
    public Result<MemosSyncResultVO> pull() {
        return Result.success(memosService.syncPull());
    }

    @PostMapping("/tag")
    @Log(title = "备忘同步", type = OperationType.OTHER)
    public Result<MemosJobResultVO> tag(@RequestBody @Valid MemosTagDTO dto) {
        return Result.success(memosService.aiTag(dto.getIds(), dto.getModelId()));
    }

    @PostMapping("/push-tags")
    @Log(title = "备忘同步", type = OperationType.OTHER)
    public Result<MemosJobResultVO> pushTags() {
        return Result.success(memosService.pushTags());
    }

    @GetMapping("/page")
    @Log(title = "备忘同步", type = OperationType.QUERY)
    public Result<Page<MemosNoteVO>> page(@ModelAttribute @Valid MemosQueryDTO query) {
        return Result.success(memosService.page(query));
    }

    @GetMapping("/stats")
    @Log(title = "备忘同步", type = OperationType.QUERY)
    public Result<MemosStatsVO> stats() {
        return Result.success(memosService.stats());
    }
}
