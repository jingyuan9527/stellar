package com.stellar.system.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.system.dto.ChangePasswordRequest;
import com.stellar.system.entity.SysUser;
import com.stellar.enums.OperationType;
import com.stellar.system.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/info")
    @Log(title = "用户管理", type = OperationType.QUERY)
    public Result<SysUser> info() {
        return Result.success(userService.getCurrentUser());
    }

    @GetMapping("/list")
    @Log(title = "用户管理", type = OperationType.QUERY)
    public Result<List<SysUser>> list() {
        return Result.success(userService.listAll());
    }

    @PostMapping("/change-password")
    @Log(title = "用户管理", type = OperationType.UPDATE)
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Result.success();
    }
}
