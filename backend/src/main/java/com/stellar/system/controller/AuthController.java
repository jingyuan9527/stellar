package com.stellar.system.controller;

import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.system.dto.LoginRequest;
import com.stellar.system.dto.LoginResult;
import com.stellar.enums.OperationType;
import com.stellar.system.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Log(title = "认证管理", type = OperationType.LOGIN)
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    @Log(title = "认证管理", type = OperationType.LOGOUT)
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }
}
