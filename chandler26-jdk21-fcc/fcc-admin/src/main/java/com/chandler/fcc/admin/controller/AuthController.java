package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.dto.ChangePasswordReq;
import com.chandler.fcc.admin.model.dto.LoginReq;
import com.chandler.fcc.admin.model.vo.LoginRespVO;
import com.chandler.fcc.admin.model.vo.UserInfoVO;
import com.chandler.fcc.admin.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Sa-Token 统一认证鉴权与登录控制 REST 控制器
 * <p>
 * 账号与口令全部以数据库为基准：控制台账号取 fcc_admin_user，坐席取 fcc_agent。
 * </p>
 *
 * @author Chandler
 */
@Tag(name = "统一认证与登录鉴权", description = "提供控制台账号与坐席工号登录、Redis Token 颁发、个人信息、口令修改与登出接口")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 统一登录接口
     *
     * @param req 登录请求
     * @return Token 及用户信息
     */
    @Operation(summary = "控制台账号与坐席工号统一登录")
    @PostMapping("/login")
    public CommonResult<LoginRespVO> login(@Valid @RequestBody LoginReq req) {
        LoginRespVO vo = authService.login(req);
        return CommonResult.success(vo);
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前会话用户信息
     */
    @Operation(summary = "获取当前登录身份信息")
    @GetMapping("/me")
    public CommonResult<UserInfoVO> me() {
        UserInfoVO vo = authService.getLoginUserInfo();
        return CommonResult.success(vo);
    }

    /**
     * 当前登录用户自助修改口令
     *
     * @param req 修改口令入参
     * @return 操作成功响应
     */
    @Operation(summary = "修改当前登录账号口令")
    @PostMapping("/change-password")
    public CommonResult<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        authService.changePassword(req);
        return CommonResult.success();
    }

    /**
     * 注销登录
     *
     * @return 操作成功响应
     */
    @Operation(summary = "安全退出登录")
    @PostMapping("/logout")
    public CommonResult<Void> logout() {
        authService.logout();
        return CommonResult.success();
    }
}
