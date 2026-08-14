package com.stellar.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
import com.stellar.interceptor.WebUtils;
import com.stellar.system.dto.ClientErrorReportDTO;
import com.stellar.system.entity.SysLog;
import com.stellar.system.service.SysLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 前端错误上报：渲染/runtime 异常由浏览器统一上报，经 SysLogService 异步落 sys_log
 * （module=前端错误），可在后台「日志管理」页按模块检索排查；游客也放行（IP 日限 200 防刷）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/system/client-error")
public class ClientErrorController {

    private final SysLogService sysLogService;

    @PostMapping
    @PublicAccess
    @RateLimit(daily = 200)
    public Result<Void> report(@RequestBody @Valid ClientErrorReportDTO dto, HttpServletRequest request) {
        SysLog sysLog = new SysLog();
        sysLog.setModule("前端错误");
        sysLog.setOperationType("OTHER");
        sysLog.setRequestUrl(dto.getUrl() != null && !dto.getUrl().isBlank() ? dto.getUrl() : "-");
        sysLog.setParams(dto.getMessage());
        sysLog.setErrorMsg(dto.getStack());
        sysLog.setStatus(1);
        sysLog.setIp(WebUtils.getClientIp(request));
        sysLog.setOperator(StpUtil.isLogin() ? "account:" + StpUtil.getLoginIdAsString() : null);
        sysLog.setCreateTime(LocalDateTime.now());
        sysLogService.saveLog(sysLog);
        log.warn("[前端错误] source={} url={} message={} stack={}",
                dto.getSource(), dto.getUrl(), dto.getMessage(), dto.getStack());
        return Result.success();
    }
}