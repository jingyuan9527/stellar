package com.stellar.system.controller;

import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.Result;
import com.stellar.system.entity.SysProfile;
import com.stellar.system.entity.SysProfileProject;
import com.stellar.system.service.MenuVisibilityService;
import com.stellar.system.service.ProfileProjectService;
import com.stellar.system.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开接口集合（游客可访问）。
 * <p>所有方法显式标注 {@link PublicAccess} 放行；方法内可用 {@code StpUtil.isLogin()}
 * 区分游客/登录做差异化处理。游客访问受 IP 限流保护（阶段3）。
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final MenuVisibilityService menuVisibilityService;
    private final ProfileService profileService;
    private final ProfileProjectService profileProjectService;

    /** 游客可见菜单的 route key 列表，前端据此过滤侧边栏与路由守卫。 */
    @PublicAccess
    @GetMapping("/menu-config")
    public Result<List<String>> menuConfig() {
        return Result.success(menuVisibilityService.listPublicRouteKeys());
    }

    /** 个人介绍（关于我 /about 展示）。 */
    @PublicAccess
    @GetMapping("/profile")
    public Result<SysProfile> profile() {
        return Result.success(profileService.get());
    }

    /** 个人项目展示列表（关于我 /about 页公开访问）。 */
    @PublicAccess
    @GetMapping("/profile-projects")
    public Result<List<SysProfileProject>> profileProjects() {
        return Result.success(profileProjectService.list());
    }
}

