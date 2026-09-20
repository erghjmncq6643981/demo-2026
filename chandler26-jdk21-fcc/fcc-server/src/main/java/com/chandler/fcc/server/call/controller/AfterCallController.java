package com.chandler.fcc.server.call.controller;

import com.chandler.fcc.server.call.application.AfterCallService;
import com.chandler.fcc.server.call.controller.req.SaveAfterCallReq;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供当前坐席已结束通话的话后小结接口。
 */
@RestController
@RequestMapping("/api/telephony/calls/{callId}/summary")
@RequiredArgsConstructor
public class AfterCallController {

    private final AfterCallService service;

    /**
     * 保存话后小结。
     *
     * @param callId 本人已结束通话标识
     * @param request 小结内容
     * @return 保存结果
     */
    @PostMapping
    public Map<String, Object> save(
        @PathVariable String callId,
        @RequestBody SaveAfterCallReq request
    ) {
        service.save(callId, request.getCategory(), request.getIntent(), request.getNotes());
        return Map.of("code", 200, "data", Map.of("saved", true));
    }

    /**
     * 查询话后小结详情。
     *
     * @param callId 本人已结束通话标识
     * @return 小结详情
     */
    @GetMapping
    public Map<String, Object> detail(@PathVariable String callId) {
        return Map.of("code", 200, "data", service.detail(callId));
    }
}
