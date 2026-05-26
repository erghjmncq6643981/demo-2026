package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dto.auth.AuthRequest;
import com.chandler.motivation.domain.dto.auth.AuthResponse;
import com.chandler.motivation.domain.dto.auth.UserProfileUpdateRequest;
import com.chandler.motivation.domain.dto.auth.UserProfileResponse;
import com.chandler.motivation.domain.dto.common.AvatarUploadResponse;
import com.chandler.motivation.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> profile() {
        return ApiResponse.ok(authService.me());
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ApiResponse.ok(authService.updateProfile(request));
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AvatarUploadResponse> updateAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(authService.updateAvatar(file));
    }

    @GetMapping("/profile/avatar")
    public ResponseEntity<byte[]> readAvatar() {
        var avatar = authService.readAvatar(authService.requireUser().getId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.data());
    }
}
