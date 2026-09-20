package com.chandler.fcc.server.agent.api;

import com.chandler.fcc.server.agent.application.PhoneBindingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 当前登录坐席的话机持有验证入口。 */
@RestController @RequestMapping("/api/telephony/phone-binding") @RequiredArgsConstructor
public class PhoneBindingController {
 private final PhoneBindingService service;
 /** 绑定请求。
  * @param extension 待验证分机
  */
 @Schema(description="话机绑定验证请求") public record Request(@Schema(description="本人正在操作的分机号码") String extension) {}
 /** 创建验证挑战。
  * @param request 分机 @return 一次绑定码
  */
 @PostMapping @Operation(summary="生成话机绑定验证码")
 public Map<String,Object> challenge(@RequestBody Request request){return Map.of("code",200,"data",service.challenge(request.extension()));}
 /** 查询本人验证状态。
  * @return 绑定结果
  */
 @GetMapping @Operation(summary="查询本人话机绑定结果")
 public Map<String,Object> status(){return Map.of("code",200,"data",service.status());}
}
