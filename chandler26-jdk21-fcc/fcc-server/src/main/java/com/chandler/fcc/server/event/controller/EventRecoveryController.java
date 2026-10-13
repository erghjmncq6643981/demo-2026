package com.chandler.fcc.server.event.controller;

import com.chandler.fcc.server.event.application.EventRecoveryService;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.management.controller.resp.ManagementResp;
import java.util.List;
import com.chandler.fcc.server.event.controller.resp.EventRecoveryIssueResp;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 显式鉴权的话务异常管理接口，不向坐席或匿名用户暴露事件载荷。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/telephony/recovery/events")
@Tag(name = "话务异常修复")
public class EventRecoveryController {
    private final EventRecoveryService service;
    private final AgentIdentityService identity;

    /** @param page 页码 @return 脱敏未知事件分页摘要 */
    @GetMapping
    @Operation(summary = "分页查询脱敏事件异常")
    public ManagementResp<List<EventRecoveryIssueResp>> list(@RequestParam(defaultValue="1") int page) {
        identity.requireRuntimeManagement();
        var response=new ManagementResp<List<EventRecoveryIssueResp>>();response.setData(service.issues(page));return response;
    }

    /** @param eventId 原始事件标识 @return 本次受控事实修复结果 */
    @PostMapping("/{eventId}/reconcile")
    @Operation(summary = "用原始结束事件修复通话事实，不重放媒体命令")
    public ManagementResp<Boolean> reconcile(@PathVariable String eventId) {
        identity.requireRuntimeManagement();service.reconcile(eventId);
        var response=new ManagementResp<Boolean>();response.setData(true);return response;
    }
}
