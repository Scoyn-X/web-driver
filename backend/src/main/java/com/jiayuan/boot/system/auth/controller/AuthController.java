package com.jiayuan.boot.system.auth.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.auth.model.vo.LoginRequestVO;
import com.jiayuan.boot.system.auth.model.vo.LoginResponseVO;
import com.jiayuan.boot.system.auth.model.vo.RegisterRequestVO;
import com.jiayuan.boot.system.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制层
 *
 * @author didongchen
 * @since 2026/04/10
 */
@Tag(name = "认证接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<Void> register(@RequestBody @Valid RegisterRequestVO request) {
        authService.register(request);
        return Result.success();
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<LoginResponseVO> login(@RequestBody @Valid LoginRequestVO request) {
        LoginResponseVO loginResponseVO = authService.login(request);
        return Result.success(loginResponseVO);
    }

}

