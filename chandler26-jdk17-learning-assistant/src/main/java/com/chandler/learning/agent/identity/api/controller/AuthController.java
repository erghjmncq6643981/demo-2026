package com.chandler.learning.agent.identity.api.controller;

import com.chandler.learning.agent.identity.api.request.AuthRequest;
import com.chandler.learning.agent.identity.api.response.AuthResponse;
import com.chandler.learning.agent.identity.api.response.UserProfileResponse;
import com.chandler.learning.agent.identity.api.request.UserProfileUpdateRequest;
import com.chandler.learning.agent.identity.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账户接口控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/auth")
@Tag(name = "学习用户认证")
public class AuthController {

    private final AuthService authService;

    /** 注册学习用户。 */
    @PostMapping("/register")
    @Operation(summary = "注册学习用户")
    public AuthResponse register(@Valid @RequestBody AuthRequest request) {
        return authService.register(request);
    }

    /** 登录学习用户。 */
    @PostMapping("/login")
    @Operation(summary = "登录学习用户")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request);
    }

    /** 当前登录用户。 */
    @GetMapping("/me")
    @Operation(summary = "当前登录用户")
    public UserProfileResponse me() {
        return authService.me();
    }

    /** 更新当前登录用户。 */
    @PutMapping("/me")
    @Operation(summary = "更新当前登录用户")
    public UserProfileResponse updateProfile(@RequestBody UserProfileUpdateRequest request) {
        return authService.updateProfile(request);
    }

    /** 退出登录。 */
    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public void logout() {
        authService.logout();
    }
}
