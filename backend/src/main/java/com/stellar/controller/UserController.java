package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.entity.SysUser;
import com.stellar.enums.OperationType;
import com.stellar.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
