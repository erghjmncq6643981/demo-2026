package com.chandler.fcc.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.dto.SystemConfigReq;
import com.chandler.fcc.admin.model.vo.SystemConfigVO;
import com.chandler.fcc.admin.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 动态系统参数配置 REST 控制器
 *
 * @author Chandler
 */
@Tag(name = "动态系统配置管理", description = "提供前端Web、后台Backend、客户端Client多作用域业务参数热更新")
@RestController
@RequestMapping("/api/admin/configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    /**
     * 保存或更新系统配置参数
     *
     * @param req 配置参数入参
     * @return 配置 ID
     */
    @Operation(summary = "保存或更新系统配置项")
    @PostMapping
    public CommonResult<Long> saveConfig(@Valid @RequestBody SystemConfigReq req) {
        Long id = configService.saveOrUpdateConfig(req, StpUtil.getLoginIdAsString());
        return CommonResult.success(id);
    }

    /**
     * 根据作用域查询配置列表
     *
     * @param scope 作用域 (WEB, BACKEND, CLIENT, SYSTEM)
     * @return 配置列表
     */
    @Operation(summary = "根据作用域获取配置参数列表")
    @GetMapping
    public CommonResult<List<SystemConfigVO>> listConfigs(
            @RequestParam(value = "scope", required = false, defaultValue = "BACKEND") String scope) {
        List<SystemConfigVO> list = configService.listConfigsByScope(scope);
        return CommonResult.success(list);
    }

    /**
     * 删除指定配置项
     *
     * @param id 配置 ID
     * @return 操作成功响应
     */
    @Operation(summary = "删除指定配置项")
    @DeleteMapping("/{id}")
    public CommonResult<Void> deleteConfig(@PathVariable("id") Long id) {
        configService.deleteConfig(id);
        return CommonResult.success();
    }
}
