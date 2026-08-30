package com.chandler.learning.agent.identity.api.controller;

import com.chandler.learning.agent.identity.api.response.AdminUserPageResponse;
import com.chandler.learning.agent.identity.api.request.AdminUserResetPasswordRequest;
import com.chandler.learning.agent.identity.api.response.AdminUserResponse;
import com.chandler.learning.agent.identity.api.request.AdminUserSaveRequest;
import com.chandler.learning.agent.identity.api.request.AdminUserUpdateRequest;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.identity.application.AdminUserService;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.security.LearningPermission;
import com.chandler.learning.agent.security.RequirePermission;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 系统管理员用户中心接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
@RequirePermission(LearningPermission.SYSTEM_ADMIN)
@Tag(name = "系统管理：用户中心")
public class AdminUserController {

    private final CurrentUserContext currentUserContext;
    private final AdminUserService adminUserService;

    /** 分页查询用户。 */
    @GetMapping
    @Operation(summary = "分页查询用户")
    public AdminUserPageResponse page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime registeredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime registeredTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastLoginFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime lastLoginTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return adminUserService.page(keyword, roleCode, enabled, registeredFrom, registeredTo,
                lastLoginFrom, lastLoginTo, page, pageSize);
    }

    /** 查看用户。 */
    @GetMapping("/{userId}")
    @Operation(summary = "查看用户")
    public AdminUserResponse detail(
            @PathVariable Long userId) {
        return adminUserService.detail(userId);
    }

    /** 新增用户。 */
    @PostMapping
    @Operation(summary = "新增用户")
    public AdminUserResponse create(
            @Valid @RequestBody AdminUserSaveRequest request) {
        LearningUser operator = currentUserContext.requireUser();
        return adminUserService.create(operator, request);
    }

    /** 修改用户。 */
    @PutMapping("/{userId}")
    @Operation(summary = "修改用户")
    public AdminUserResponse update(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserUpdateRequest request) {
        LearningUser operator = currentUserContext.requireUser();
        return adminUserService.update(operator, userId, request);
    }

    /** 重置用户密码。 */
    @PostMapping("/{userId}/reset-password")
    @Operation(summary = "重置用户密码")
    public void resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody AdminUserResetPasswordRequest request) {
        LearningUser operator = currentUserContext.requireUser();
        adminUserService.resetPassword(operator, userId, request);
    }

    /** 注销用户。 */
    @DeleteMapping("/{userId}")
    @Operation(summary = "注销用户")
    public void delete(
            @PathVariable Long userId) {
        LearningUser operator = currentUserContext.requireUser();
        adminUserService.delete(operator, userId);
    }
}
