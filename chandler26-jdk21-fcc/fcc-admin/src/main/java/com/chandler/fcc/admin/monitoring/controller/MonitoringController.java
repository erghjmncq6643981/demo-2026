package com.chandler.fcc.admin.monitoring.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.monitoring.application.MonitoringService;
import com.chandler.fcc.admin.monitoring.controller.req.MonitoringReq;
import com.chandler.fcc.admin.monitoring.controller.resp.MonitoringBucketResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 话务监控聚合入口，权限与 CDR 一致，不返回个人通话资料。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/monitoring")
@Tag(name="话务监控聚合")
public class MonitoringController {
    private final MonitoringService service;
    /** @param request 周期与定位日期 @return 全量聚合 */
    @GetMapping @Operation(summary="按日周月聚合完整话务指标")
    public CommonResult<List<MonitoringBucketResp>> aggregate(@Valid MonitoringReq request) {
        StpUtil.checkPermission("cdr:view");
        return CommonResult.success(service.aggregate(request));
    }
}
