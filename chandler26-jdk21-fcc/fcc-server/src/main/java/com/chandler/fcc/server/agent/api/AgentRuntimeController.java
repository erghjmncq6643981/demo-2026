package com.chandler.fcc.server.agent.api;
import com.chandler.fcc.server.agent.application.AgentRuntimeService;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 坐席工作状态及话后整理完成接口。 */
@RestController @RequestMapping("/api/telephony/agent-state") @RequiredArgsConstructor
public class AgentRuntimeController {
 private final AgentRuntimeService service;
 /** 状态修改请求。 @param status READY 或 REST */
 @Schema(description="坐席工作状态修改") public record Request(@Schema(description="就绪 READY 或休息 REST") String status){}
 /** 查询本人状态。 @return 权威工作状态 */
 @GetMapping public Map<String,Object> state(){return Map.of("code",200,"data",service.status());}
 /** 提交状态或完成整理。 @param request 状态 @return 权威工作状态 */
 @PostMapping public Map<String,Object> change(@RequestBody Request request){return Map.of("code",200,"data",service.change(request.status()));}
}
