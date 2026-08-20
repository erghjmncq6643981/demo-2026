package com.chandler.learning.agent.identity.api;

import com.chandler.learning.agent.identity.api.AdminUserPageResponse;
import com.chandler.learning.agent.identity.api.AdminUserResetPasswordRequest;
import com.chandler.learning.agent.identity.api.AdminUserResponse;
import com.chandler.learning.agent.identity.api.AdminUserSaveRequest;
import com.chandler.learning.agent.identity.api.AdminUserUpdateRequest;
import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.identity.application.AdminUserService;
import com.chandler.learning.agent.identity.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 系统管理员用户中心接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
@Tag(name = "系统管理：用户中心")
public class AdminUserController {

    private final AuthService authService;
    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "分页查询用户")
    public AdminUserPageResponse page(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime registeredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime registeredTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastLoginFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastLoginTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        authService.requireAdmin(authorization);
        return adminUserService.page(keyword, roleCode, enabled, registeredFrom, registeredTo,
                lastLoginFrom, lastLoginTo, page, pageSize);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "查看用户")
    public AdminUserResponse detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long userId) {
        authService.requireAdmin(authorization);
        return adminUserService.detail(userId);
    }

    @PostMapping
    @Operation(summary = "新增用户")
    public AdminUserResponse create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AdminUserSaveRequest request) {
        LearningUser operator = authService.requireAdmin(authorization);
        return adminUserService.create(operator, request);
    }

    @PutMapping("/{userId}")
    @Operation(summary = "修改用户")
    public AdminUserResponse update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserUpdateRequest request) {
        LearningUser operator = authService.requireAdmin(authorization);
        return adminUserService.update(operator, userId, request);
    }

    @PostMapping("/{userId}/reset-password")
    @Operation(summary = "重置用户密码")
    public void resetPassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserResetPasswordRequest request) {
        LearningUser operator = authService.requireAdmin(authorization);
        adminUserService.resetPassword(operator, userId, request);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "注销用户")
    public void delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long userId) {
        LearningUser operator = authService.requireAdmin(authorization);
        adminUserService.delete(operator, userId);
    }
}
