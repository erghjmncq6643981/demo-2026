package com.chandler.fcc.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.dto.AdminUserCreateReq;
import com.chandler.fcc.admin.model.dto.AdminUserUpdateReq;
import com.chandler.fcc.admin.model.dto.ResetPasswordReq;
import com.chandler.fcc.admin.model.vo.AccountCredentialVO;
import com.chandler.fcc.admin.model.vo.AdminUserVO;
import com.chandler.fcc.admin.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理控制台账号治理 REST 控制器
 * <p>
 * 控制台账号全部以 fcc_admin_user 表数据为基准，不存在任何内置账号。
 * 账号的增删改与口令重置仅对系统超级管理员开放。
 * </p>
 *
 * @author Chandler
 */
@Tag(name = "控制台账号管理", description = "系统超管与运营账号的创建、启停、角色调整与口令重置")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 查询全部控制台账号
     *
     * @return 账号列表
     */
    @Operation(summary = "查询控制台账号列表")
    @GetMapping
    public CommonResult<List<AdminUserVO>> listUsers() {
        requireSuperAdmin();
        return CommonResult.success(adminUserService.listUsers());
    }

    /**
     * 按主键查询控制台账号
     *
     * @param id 账号主键 ID
     * @return 账号详情
     */
    @Operation(summary = "查询控制台账号详情")
    @GetMapping("/{id}")
    public CommonResult<AdminUserVO> getUser(@PathVariable("id") Long id) {
        requireSuperAdmin();
        return CommonResult.success(adminUserService.getUser(id));
    }

    /**
     * 创建控制台账号
     *
     * @param req 账号创建入参
     * @return 账号创建结果 (可能包含一次性初始口令)
     */
    @Operation(summary = "创建控制台账号 (口令留空则返回一次性随机口令)")
    @PostMapping
    public CommonResult<AccountCredentialVO> createUser(@Valid @RequestBody AdminUserCreateReq req) {
        requireSuperAdmin();
        return CommonResult.success(adminUserService.createUser(req));
    }

    /**
     * 更新控制台账号
     *
     * @param req 账号更新入参
     * @return 操作成功响应
     */
    @Operation(summary = "更新控制台账号资料与状态")
    @PutMapping
    public CommonResult<Void> updateUser(@Valid @RequestBody AdminUserUpdateReq req) {
        requireSuperAdmin();
        adminUserService.updateUser(req);
        return CommonResult.success();
    }

    /**
     * 逻辑删除控制台账号
     *
     * @param id 账号主键 ID
     * @return 操作成功响应
     */
    @Operation(summary = "删除控制台账号")
    @DeleteMapping("/{id}")
    public CommonResult<Void> deleteUser(@PathVariable("id") Long id) {
        requireSuperAdmin();
        AdminUserVO target = adminUserService.getUser(id);
        if (target.getUsername().equals(StpUtil.getLoginIdAsString())) {
            throw new IllegalArgumentException("不能删除当前登录账号自身");
        }
        adminUserService.deleteUser(id);
        return CommonResult.success();
    }

    /**
     * 重置指定账号口令
     *
     * @param id  账号主键 ID
     * @param req 口令重置入参
     * @return 口令重置结果 (可能包含一次性新口令)
     */
    @Operation(summary = "重置控制台账号口令")
    @PostMapping("/{id}/reset-password")
    public CommonResult<AccountCredentialVO> resetPassword(@PathVariable("id") Long id,
                                                          @Valid @RequestBody ResetPasswordReq req) {
        requireSuperAdmin();
        return CommonResult.success(adminUserService.resetPassword(id, req == null ? null : req.getPassword()));
    }

    /**
     * 校验当前请求方具备系统超级管理员角色
     * <p>
     * 账号治理属于最高敏感操作，即便同为控制台账号也仅超管可操作。
     * </p>
     */
    private void requireSuperAdmin() {
        StpUtil.checkLogin();
        if (!StpUtil.hasRole("ADMIN")) {
            throw new IllegalArgumentException("仅系统超级管理员可管理控制台账号");
        }
    }
}
