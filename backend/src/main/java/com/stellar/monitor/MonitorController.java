package com.stellar.monitor;

import com.stellar.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 系统监控：实时快照接口（需登录，不标注 @PublicAccess，禁止匿名访问）。
 * <p>仅对内维护排查使用，数据全部来自本进程内存实时读取。
 */
@Slf4j
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;

    /**
     * 监控概览实时快照。
     */
    @GetMapping("/overview")
    public Result<MonitorOverviewVO> overview() {
        return Result.success(monitorService.overview());
    }

    /**
     * 导出 Markdown 监控报告（可直接粘贴给 AI 分析 JVM 调优）。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export() {
        String content = monitorService.exportMarkdown();
        String filename = "stellar-monitor-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}