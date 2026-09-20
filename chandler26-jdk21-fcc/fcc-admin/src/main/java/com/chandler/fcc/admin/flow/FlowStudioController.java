package com.chandler.fcc.admin.flow;

import com.chandler.fcc.admin.model.CommonResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 管理画布的新建入口与只读通话过程入口。 */
@RestController @RequestMapping("/api/admin/flow-studio") @RequiredArgsConstructor
public class FlowStudioController {
    private final FlowStudioService service;
    /** 新建请求。 @param flowKey 流程业务代码 @param flowName 流程名称 */
    @Schema(description="创建呼入流程")
    public record CreateRequest(@Schema(description="流程代码，只允许字母数字下划线和连字符") String flowKey,
                                @Schema(description="流程业务名称") String flowName) {}
    /** 创建流程元数据。 @param request 创建参数 @return 流程代码 */
    @PostMapping public CommonResult<String> create(@RequestBody CreateRequest request){return CommonResult.success(service.create(request.flowKey(),request.flowName()));}
    /** 查看单通话阶段事实。 @param callId 通话 ID @param after 上页末尾记录 ID @return 快照与阶段列表 */
    @GetMapping("/calls/{callId}") public CommonResult<Map<String,Object>> execution(@PathVariable String callId,@RequestParam(defaultValue="0") String after){return CommonResult.success(service.execution(callId,after));}
}
