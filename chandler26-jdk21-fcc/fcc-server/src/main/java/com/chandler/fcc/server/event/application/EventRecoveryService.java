package com.chandler.fcc.server.event.application;

import com.chandler.fcc.server.call.application.CallFinalizationService;
import com.chandler.fcc.server.event.infrastructure.EventInboxMapper;
import com.chandler.fcc.server.event.controller.resp.EventRecoveryIssueResp;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 未知事件的受控处理：只修复可信 DESTROY 事实，绝不重放拨号或桥接副作用。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventRecoveryService {
    private final EventInboxMapper inbox;
    private final CallFinalizationService finalization;
    private final AgentIdentityService identity;
    private final ObjectMapper json;

    /** @param page 页码 @return 脱敏异常摘要，不返回原始载荷 */
    public List<EventRecoveryIssueResp> issues(int page) {
        identity.requireRuntimeManagement();
        if(page<1 || page>100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"页码不合法");
        return inbox.recoveryIssues((page-1)*50);
    }

    /**
     * 人工触发真实结束事实修复，成功后关闭该异常；无法判定时保持 UNKNOWN。
     * @param eventId 原始事件标识
     * @throws ResponseStatusException 事件不是可修复终态、缺少通话关联或已被其他实例领取
     */
    public void reconcile(String eventId) {
        String actor=identity.requireRuntimeManagement().workNo();
        if(eventId==null || !eventId.matches("[a-f0-9]{64}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"事件标识不合法");
        String token = UUID.randomUUID().toString();
        if(inbox.claimRecovery(eventId, token, actor)!=1)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"事件已处理、正在修复或已达十次修复上限");
        boolean reconciled = false;
        try {
            if (inbox.recordRecoveryAudit(eventId, token, actor) != 1)
                throw new IllegalStateException("无法记录修复审计");
            var root=json.readTree(inbox.recoveryPayload(eventId));
            var params=root.path("params");
            if (!eventId.equals(params.path("event_id").asText())
                || params.path("uuid").asText().isBlank() || params.path("node_id").asText().isBlank()
                || (params.path("timestamp").asLong() <= 0 && params.path("end_epoch").asLong() <= 0))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"原始事件缺少可校验的身份或源时间");
            if(!"Event.Channel".equals(root.path("method").asText()) || !"DESTROY".equals(params.path("state").asText()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"非结束事件不能自动重放，请核对业务事实");
            var call=finalization.resolveCall(params.path("ctrl_uuid").asText(null),params.path("uuid").asText(null),params.path("node_id").asText(null));
            if(call==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"缺少精确的通话或话道关联");
            finalization.recordChannelEnd(call.getCallId(),params);
            finalization.cleanupEndedCall(Long.valueOf(call.getCallId()));
            if(inbox.finishRecovery(eventId,token,"RECONCILED",null)!=1)
                throw new IllegalStateException("修复结果写入失败");
            reconciled = true;
            inbox.finishRecoveryAudit(token, "RECONCILED", null);
            log.info("[异常修复] 结束事实已对账 actor={} eventId={} callId={}",actor,eventId,call.getCallId());
        } catch(Exception failure) {
            if (reconciled) {
                log.error("[异常修复] 事实已修复但动作审计完成写入失败 eventId={} actionId={} type={}",
                    eventId,token,failure.getClass().getSimpleName());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"事实已修复，审计完成状态需对账");
            }
            try {
                inbox.finishRecovery(eventId,token,"UNKNOWN",failure.getClass().getSimpleName());
                inbox.finishRecoveryAudit(token,"UNKNOWN",failure.getClass().getSimpleName());
            } catch (RuntimeException auditFailure) {
                log.error("[异常修复] 结果写入失败，保留租约等待重新领取 eventId={} type={}",
                    eventId,auditFailure.getClass().getSimpleName());
            }
            if(failure instanceof ResponseStatusException business) throw business;
            throw new ResponseStatusException(HttpStatus.CONFLICT,"修复未完成，异常保留待重试");
        }
    }
}
