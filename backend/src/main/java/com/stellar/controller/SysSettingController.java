package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.enums.OperationType;
import com.stellar.service.SysSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统设置接口（需登录）。管理全局开关/单值配置，如 conch_ai_enabled。
 */
@Slf4j
@RestController
@RequestMapping("/setting")
@RequiredArgsConstructor
public class SysSettingController {

    private final SysSettingService sysSettingService;

    @GetMapping("/{key}")
    @Log(title = "系统设置", type = OperationType.QUERY)
    public Result<String> get(@PathVariable String key) {
        return Result.success(sysSettingService.get(key, null));
    }

    @PutMapping("/{key}")
    @Log(title = "系统设置", type = OperationType.UPDATE)
    public Result<Void> set(@PathVariable String key, @RequestParam String value) {
        sysSettingService.set(key, value, null);
        return Result.success();
    }
}
