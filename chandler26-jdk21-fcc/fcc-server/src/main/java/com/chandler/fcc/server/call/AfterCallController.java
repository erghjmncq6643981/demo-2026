package com.chandler.fcc.server.call;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** 本人已结束通话的话后小结接口。 */
@RestController @RequestMapping("/api/telephony/calls/{callId}/summary") @RequiredArgsConstructor
public class AfterCallController {
    private final AfterCallService service;
    /** 话后整理输入。
     * @param category 业务类别 @param intent 意向评估 @param notes 沟通纪要
     */
    @Schema(description="坐席话后小结")
    public record Request(@Schema(description="业务类别，最多64字") String category,
                          @Schema(description="意向评估：HIGH、MID、LOW、UNASSESSED") String intent,
                          @Schema(description="沟通纪要，最多2000字") String notes) {}
    /** 提交小结。
     * @param callId 本人通话标识 @param request 小结内容 @return 成功响应
     */
    @PostMapping public Map<String,Object> save(@PathVariable String callId,@RequestBody Request request){service.save(callId,request.category(),request.intent(),request.notes());return Map.of("code",200,"data",Map.of("saved",true));}
    /** 查询小结详情。
     * @param callId 本人通话标识 @return 小结内容
     */
    @GetMapping public Map<String,Object> detail(@PathVariable String callId){return Map.of("code",200,"data",service.detail(callId));}
}
