package com.stellar.system.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.system.dto.MenuVisibilityItemDTO;
import com.stellar.system.entity.SysMenuVisibility;
import com.stellar.enums.OperationType;
import com.stellar.system.service.MenuVisibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单可见性配置（管理后台，需登录）。
 */
@RestController
@RequestMapping("/menu-visibility")
@RequiredArgsConstructor
public class MenuVisibilityController {

    private final MenuVisibilityService menuVisibilityService;

    @GetMapping("/list")
    @Log(title = "菜单可见性", type = OperationType.QUERY)
    public Result<List<SysMenuVisibility>> list() {
        return Result.success(menuVisibilityService.listAll());
    }

    @PostMapping("/batch")
    @Log(title = "菜单可见性", type = OperationType.UPDATE)
    public Result<Void> batchUpdate(@RequestBody List<MenuVisibilityItemDTO> items) {
        menuVisibilityService.batchUpsert(items);
        return Result.success();
    }
}
