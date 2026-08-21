package com.stellar.system.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.system.dto.ProfileDTO;
import com.stellar.system.entity.SysProfile;
import com.stellar.enums.OperationType;
import com.stellar.system.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人介绍管理（管理后台，需登录）。单条配置。
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    @Log(title = "个人介绍", type = OperationType.QUERY)
    public Result<SysProfile> get() {
        return Result.success(profileService.get());
    }

    @PutMapping
    @Log(title = "个人介绍", type = OperationType.UPDATE)
    public Result<Void> update(@Valid @RequestBody ProfileDTO dto) {
        profileService.update(dto);
        return Result.success();
    }
}
