package com.chandler.fcc.admin.controller;

import com.chandler.fcc.admin.model.CommonResult;
import com.chandler.fcc.admin.model.PageResult;
import com.chandler.fcc.admin.model.dto.CallCdrQueryReq;
import com.chandler.fcc.admin.model.vo.CallCdrStatsVO;
import com.chandler.fcc.admin.model.vo.CallCdrVO;
import com.chandler.fcc.admin.service.CallCdrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 历史通话话单 (CDR) 检索与录音回放 REST 控制器
 *
 * @author Chandler
 */
@Tag(name = "通话话单与录音管理", description = "提供全量话单检索、分段信道Leg详情、录音回溯与质检评估查询")
@RestController
@RequestMapping("/api/admin/cdrs")
@RequiredArgsConstructor
public class CallCdrController {

    private final CallCdrService cdrService;

    /**
     * 多条件分页查询通话话单 CDR
     *
     * @param req 过滤参数
     * @return 话单分页结果
     */
    @Operation(summary = "多条件分页查询历史通话话单CDR")
    @GetMapping
    public CommonResult<PageResult<CallCdrVO>> queryCdrs(CallCdrQueryReq req) {
        PageResult<CallCdrVO> result = cdrService.queryCdrs(req);
        return CommonResult.success(result);
    }

    /**
     * 查询今日话单 KPI 聚合指标
     * <p>
     * 与分页列表解耦：由数据库直接聚合，供工作台顶部指标卡片使用。
     * </p>
     *
     * @return 指标聚合结果
     */
    @Operation(summary = "查询今日话单KPI聚合指标 (呼叫总数/接通数/通话时长)")
    @GetMapping("/stats")
    public CommonResult<CallCdrStatsVO> getCdrStats() {
        return CommonResult.success(cdrService.queryTodayStats());
    }

    /**
     * 查询通话话单详情与信道 Leg 轨迹
     *
     * @param id 会话主键 ID
     * @return 话单详情聚合视图
     */
    @Operation(summary = "获取单通会话详情与信道Leg轨迹")
    @GetMapping("/{id}")
    public CommonResult<CallCdrVO> getCdrDetail(@PathVariable("id") Long id) {
        CallCdrVO vo = cdrService.getCdrDetail(id);
        return CommonResult.success(vo);
    }
}
