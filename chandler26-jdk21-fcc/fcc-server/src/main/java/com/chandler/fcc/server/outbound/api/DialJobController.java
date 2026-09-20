package com.chandler.fcc.server.outbound.api;
import com.chandler.fcc.server.outbound.application.DialJobService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 自动外呼任务管理接口，任务归属来自当前登录身份。 */
@RestController @RequestMapping("/api/telephony/dial-jobs") @RequiredArgsConstructor
public class DialJobController {
 private final DialJobService service;
 /** 创建请求。 @param number 目标 @param mode 模式 @param maxAttempts 次数 @param requestKey 去重键 */
 @Schema(description="自动外呼创建请求") public record Request(@Schema(description="客户号码")String number,@Schema(description="NOTIFICATION 通知或 PROGRESSIVE 渐进式")String mode,@Schema(description="最多尝试次数，一至三")int maxAttempts,@Schema(description="客户端生成的稳定请求标识")String requestKey){}
 /** 操作请求。 @param action 暂停、继续或取消 */
 @Schema(description="自动外呼操作") public record Control(@Schema(description="PAUSE、RESUME 或 CANCEL")String action){}
 /** 创建。 @param request 请求 @return 任务标识 */
 @PostMapping public Map<String,Object> create(@RequestBody Request request){return Map.of("code",200,"data",Map.of("id",service.create(request.number(),request.mode(),request.maxAttempts(),request.requestKey())));}
 /** 分页查询。 @param page 页码 @return 摘要 */
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="1")int page){return Map.of("code",200,"data",service.list(page));}
 /** 详情。 @param id 任务 @return 逐次结果 */
 @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable String id){return Map.of("code",200,"data",service.detail(id));}
 /** 操作。 @param id 任务 @param request 操作 @return 受理结果 */
 @PostMapping("/{id}/state") public Map<String,Object> control(@PathVariable String id,@RequestBody Control request){service.control(id,request.action());return Map.of("code",200,"data",Map.of("id",id));}
}
