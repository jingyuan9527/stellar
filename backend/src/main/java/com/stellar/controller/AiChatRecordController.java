package com.stellar.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.dto.AiChatRecordQueryDTO;
import com.stellar.enums.OperationType;
import com.stellar.service.SysAiChatRecordService;
import com.stellar.vo.AiChatRecordVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 文本生成历史接口（流式结束自动落库的记录）。
 * <p>对游客开放（@PublicAccess）：登录按账号、游客按 IP 查看自己的历史。
 */
@Slf4j
@RestController
@RequestMapping("/ai/chat/record")
@RequiredArgsConstructor
public class AiChatRecordController {

    private final SysAiChatRecordService sysAiChatRecordService;

    /**
     * 分页查询当前主体的文本生成历史（登录按账号、游客按 IP）。
     */
    @PublicAccess
    @GetMapping("/page")
    @Log(title = "文本生成历史", type = OperationType.QUERY)
    public Result<Page<AiChatRecordVO>> page(@ModelAttribute AiChatRecordQueryDTO query, HttpServletRequest request) {
        String subjectType = StpUtil.isLogin() ? "account" : "ip";
        String subjectId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : getClientIp(request);
        return Result.success(sysAiChatRecordService.page(query, subjectType, subjectId));
    }

    /**
     * 删除单条历史（校验主体归属）。
     */
    @PublicAccess
    @DeleteMapping("/{id}")
    @Log(title = "文本生成历史", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        String subjectType = StpUtil.isLogin() ? "account" : "ip";
        String subjectId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : getClientIp(request);
        sysAiChatRecordService.delete(id, subjectType, subjectId);
        return Result.success();
    }

    /**
     * 清空当前主体的全部历史。
     */
    @PublicAccess
    @DeleteMapping
    @Log(title = "文本生成历史", type = OperationType.DELETE)
    public Result<Void> clear(HttpServletRequest request) {
        String subjectType = StpUtil.isLogin() ? "account" : "ip";
        String subjectId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : getClientIp(request);
        sysAiChatRecordService.clear(subjectType, subjectId);
        return Result.success();
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
