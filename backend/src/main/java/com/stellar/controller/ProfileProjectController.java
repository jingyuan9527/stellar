package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.dto.ProfileProjectDTO;
import com.stellar.entity.SysProfileProject;
import com.stellar.enums.OperationType;
import com.stellar.service.ProfileProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人项目展示管理（管理后台，需登录）。
 * <p>公开读取走 {@link PublicController#profileProjects()}，不在此暴露。
 */
@RestController
@RequestMapping("/profile/project")
@RequiredArgsConstructor
public class ProfileProjectController {

    private final ProfileProjectService profileProjectService;

    @GetMapping("/list")
    @Log(title = "个人项目", type = OperationType.QUERY)
    public Result<List<SysProfileProject>> list() {
        return Result.success(profileProjectService.list());
    }

    @PostMapping
    @Log(title = "个人项目", type = OperationType.INSERT)
    public Result<Void> create(@Valid @RequestBody ProfileProjectDTO dto) {
        profileProjectService.create(dto);
        return Result.success();
    }

    @PutMapping
    @Log(title = "个人项目", type = OperationType.UPDATE)
    public Result<Void> update(@Valid @RequestBody ProfileProjectDTO dto) {
        profileProjectService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(title = "个人项目", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        profileProjectService.delete(id);
        return Result.success();
    }
}
