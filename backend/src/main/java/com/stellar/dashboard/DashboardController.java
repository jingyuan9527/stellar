package com.stellar.dashboard;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.enums.OperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘聚合统计：AI token + 任务质量 + 文件 + TTS 概览。
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 仪表盘全部统计（管理后台首页，需登录）。
     */
    @GetMapping("/stats")
    @Log(title = "仪表盘统计", type = OperationType.QUERY)
    public Result<DashboardStatsVO> stats() {
        return Result.success(dashboardService.stats());
    }
}
