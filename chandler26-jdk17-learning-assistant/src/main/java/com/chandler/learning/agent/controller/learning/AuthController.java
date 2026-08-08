package com.chandler.learning.agent.controller.learning;

import com.chandler.learning.agent.domain.dto.learning.AuthRequest;
import com.chandler.learning.agent.domain.dto.learning.AuthResponse;
import com.chandler.learning.agent.domain.dto.learning.UserProfileResponse;
import com.chandler.learning.agent.domain.dto.learning.UserProfileUpdateRequest;
import com.chandler.learning.agent.service.learning.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController 类。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/auth")
@Tag(name = "学习用户认证")
public class AuthController {

    private final AuthService authService;

    /**
     * 创建或保存 {@code register} 相关业务。
     */
    @PostMapping("/register")
    @Operation(summary = "注册学习用户")
    public AuthResponse register(@Valid @RequestBody AuthRequest request) {
        return authService.register(request);
    }

    /**
     * 处理 {@code login} 相关业务。
     */
    @PostMapping("/login")
    @Operation(summary = "登录学习用户")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "当前登录用户")
    public UserProfileResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(authorization);
    }

    /**
     * 更新 {@code updateProfile} 相关业务。
     */
    @PutMapping("/me")
    @Operation(summary = "更新当前登录用户")
    public UserProfileResponse updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UserProfileUpdateRequest request) {
        return authService.updateProfile(authorization, request);
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
    }
}
