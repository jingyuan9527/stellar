package com.stellar.memos.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.BusinessException;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.enums.OperationType;
import com.stellar.memos.dto.MemosConfigDTO;
import com.stellar.memos.dto.MemosQueryDTO;
import com.stellar.memos.dto.MemosTagDTO;
import com.stellar.memos.dto.MemosWebhookConfigDTO;
import com.stellar.memos.service.MemosService;
import com.stellar.memos.vo.MemosConfigVO;
import com.stellar.memos.vo.MemosJobResultVO;
import com.stellar.memos.vo.MemosNoteVO;
import com.stellar.memos.vo.MemosStatsVO;
import com.stellar.memos.vo.MemosSyncLogVO;
import com.stellar.memos.vo.MemosSyncResultVO;
import com.stellar.memos.vo.MemosWebhookConfigVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
        return Result.success(memosService.syncPullManual());
    }

    @GetMapping("/sync-log/page")
    @Log(title = "备忘同步", type = OperationType.QUERY)
    public Result<Page<MemosSyncLogVO>> syncLogPage(@ModelAttribute @Valid MemosQueryDTO query) {
        return Result.success(memosService.pageSyncLog(query));
    }

    @GetMapping("/sync-log/latest")
    @Log(title = "备忘同步", type = OperationType.QUERY)
    public Result<MemosSyncLogVO> latestSyncLog() {
        return Result.success(memosService.latestSyncLog());
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

    // ===== Webhook =====

    @GetMapping("/webhook/config")
    @Log(title = "备忘同步", type = OperationType.QUERY)
    public Result<MemosWebhookConfigVO> getWebhookConfig() {
        return Result.success(memosService.getWebhookConfig());
    }

    @PutMapping("/webhook/config")
    @Log(title = "备忘同步", type = OperationType.UPDATE)
    public Result<Void> saveWebhookSecret(@RequestBody MemosWebhookConfigDTO dto) {
        memosService.saveWebhookSecret(dto.getSecret());
        return Result.success();
    }

    /**
     * Memos webhook 投递端点（游客可达，靠签名验证安全）。
     * 成功返回 {@code {"code":0}}（Memos 校验 code==0 才视为投递成功），
     * 签名失败返回 4xx + 非 0 code，触发 Memos 重试。
     * 注意：不能走统一 Result 壳（code=200 会被 Memos 判为失败）。
     */
    @PostMapping("/webhook")
    @PublicAccess
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
            @RequestHeader(value = "webhook-signature", required = false) String signature) {
        try {
            memosService.handleWebhook(rawBody, webhookId, timestamp, signature);
            return ResponseEntity.ok(Map.of("code", 0));
        } catch (BusinessException e) {
            log.warn("[备忘同步] Webhook 请求被拒绝: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("code", 1, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("[备忘同步] Webhook 处理异常: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 2, "message", "internal error"));
        }
    }
}
