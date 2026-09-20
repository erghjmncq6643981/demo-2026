package com.chandler.fcc.server.management;

import com.chandler.fcc.server.customer.api.CustomerRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 管理前端使用的运行业务入口，所有用例在线验证管理员及租户。 */
@RestController @RequestMapping("/api/telephony/management") @RequiredArgsConstructor
public class BusinessManagementController {
    private final BusinessManagementService service;
    /** 外呼创建参数。 @param owner 执行坐席 @param number 号码 @param mode 模式 @param maxAttempts 次数 @param requestKey 幂等键 */
    @Schema(description="管理员创建外呼任务")
    public record JobRequest(@Schema(description="执行坐席工号") String owner,@Schema(description="被叫号码") String number,
        @Schema(description="模式 PROGRESSIVE 或 NOTIFICATION") String mode,@Schema(description="最多尝试次数，1 至 3") int maxAttempts,
        @Schema(description="客户端生成的唯一请求标识") String requestKey) {}
    /** 统一成功信封。 @param value 结果 @return 响应 */
    private Map<String,Object> ok(Object value){return Map.of("code",200,"data",value);}
    /** 客户摘要。 @param page 页码 @param owner 工号过滤 @param phone 号码过滤 @return 列表 */
    @GetMapping("/customers") public Map<String,Object> customers(@RequestParam(defaultValue="1") int page,@RequestParam(required=false) String owner,@RequestParam(required=false) String phone){return ok(service.customers(page,owner,phone));}
    /** 客户详情。 @param id 客户 @return 详情 */
    @GetMapping("/customers/{id}") public Map<String,Object> customer(@PathVariable String id){return ok(service.customer(id));}
    /** 创建客户。 @param owner 负责坐席 @param record 资料 @return ID */
    @PostMapping("/customers") public Map<String,Object> create(@RequestParam String owner,@RequestBody CustomerRecord record){return ok(service.save(null,owner,record));}
    /** 编辑客户。 @param id 客户 @param record 资料与版本 @return ID */
    @PutMapping("/customers/{id}") public Map<String,Object> update(@PathVariable String id,@RequestBody CustomerRecord record){return ok(service.save(id,null,record));}
    /** 外呼摘要。 @param page 页码 @param owner 工号过滤 @return 列表 */
    @GetMapping("/dial-jobs") public Map<String,Object> jobs(@RequestParam(defaultValue="1") int page,@RequestParam(required=false) String owner){return ok(service.jobs(page,owner));}
    /** 创建任务。 @param request 调度参数 @return ID */
    @PostMapping("/dial-jobs") public Map<String,Object> createJob(@RequestBody JobRequest request){return ok(service.createJob(request.owner(),request.number(),request.mode(),request.maxAttempts(),request.requestKey()));}
    /** 尝试详情。 @param id 任务 @return 逐次结果 */
    @GetMapping("/dial-jobs/{id}/attempts") public Map<String,Object> attempts(@PathVariable String id){return ok(service.attempts(id));}
    /** 任务操作。 @param id 任务 @param action 操作 @return 结果 */
    @PostMapping("/dial-jobs/{id}/{action}") public Map<String,Object> control(@PathVariable String id,@PathVariable String action){service.control(id,action);return ok(Map.of("status","UPDATED"));}
}
