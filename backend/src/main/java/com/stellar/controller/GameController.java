package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
import com.stellar.dto.GameScoreSubmitDTO;
import com.stellar.enums.OperationType;
import com.stellar.service.GameScoreService;
import com.stellar.vo.GameScoreVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 游戏接口（数学游戏等）。
 * <p>对游客开放：可游玩、可提交成绩、可查看排行榜；提交接口受 IP 单日限流防刷。
 */
@Slf4j
@RestController
@RequestMapping("/game")
@RequiredArgsConstructor
public class GameController {

    private final GameScoreService gameScoreService;

    /**
     * 提交一局游戏成绩（游客可提交，受 IP 限流保护）。
     */
    @PublicAccess
    @RateLimit(daily = 30)
    @PostMapping("/scores")
    @Log(title = "游戏成绩", type = OperationType.INSERT)
    public Result<GameScoreVO> submit(@Valid @RequestBody GameScoreSubmitDTO dto, HttpServletRequest request) {
        return Result.success(gameScoreService.submit(dto, getClientIp(request)));
    }

    /**
     * 排行榜前 100（公共墙，游客可读）。
     */
    @PublicAccess
    @GetMapping("/scores/top")
    @Log(title = "游戏排行榜", type = OperationType.QUERY)
    public Result<List<GameScoreVO>> topScores() {
        return Result.success(gameScoreService.topScores());
    }

    /**
     * 解析客户端真实 IP，穿透代理头（与 RateLimitInterceptor 一致）。
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
