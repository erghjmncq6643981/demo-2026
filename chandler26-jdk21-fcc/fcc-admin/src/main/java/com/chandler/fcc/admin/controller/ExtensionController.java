package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.ExtensionCreateReq;
import com.chandler.fcc.admin.model.dto.ExtensionQueryReq;
import com.chandler.fcc.admin.model.vo.ExtensionVO;
import com.chandler.fcc.admin.service.ExtensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 通信分机与 FreeSWITCH 配置下发 REST 控制器
 *
 * @author Chandler
 */
@Tag(name = "通信分机管理", description = "提供分机创建、FreeSWITCH Sidecar配置同步、实时注册态检索与注销接口")
@RestController
@RequestMapping("/api/admin/extensions")
@RequiredArgsConstructor
public class ExtensionController {

    private final ExtensionService extensionService;

    /**
     * 创建通信分机并同步下发至 FreeSWITCH
     *
     * @param req 分机创建入参
     * @return 分机主键 ID
     */
    @Operation(summary = "创建分机并下发FreeSWITCH配置")
    @PostMapping
    public CommonResult<Long> createExtension(@Valid @RequestBody ExtensionCreateReq req) {
        Long id = extensionService.createExtension(req);
        return CommonResult.success(id);
    }

    /**
     * 多条件分页检索分机列表
     *
     * @param req 检索入参
     * @return 分机分页结果
     */
    @Operation(summary = "多条件分页检索分机列表 (集成Redis实时在线态)")
    @GetMapping
    public CommonResult<PageResult<ExtensionVO>> queryExtensions(ExtensionQueryReq req) {
        PageResult<ExtensionVO> result = extensionService.queryExtensions(req);
        return CommonResult.success(result);
    }

    /**
     * 根据分机号码获取详情
     *
     * @param extension 分机号
     * @return 分机详情 VO
     */
    @Operation(summary = "获取单个分机详情与在线态")
    @GetMapping("/{extension}")
    public CommonResult<ExtensionVO> getByExtension(@PathVariable("extension") String extension) {
        ExtensionVO vo = extensionService.getByExtension(extension);
        return CommonResult.success(vo);
    }

    /**
     * 删除分机并从 FreeSWITCH 移除
     *
     * @param id 分机主键 ID
     * @return 操作成功响应
     */
    @Operation(summary = "删除分机并同步清理FreeSWITCH配置")
    @DeleteMapping("/{id}")
    public CommonResult<Void> deleteExtension(@PathVariable("id") Long id) {
        extensionService.deleteExtension(id);
        return CommonResult.success();
    }
}
