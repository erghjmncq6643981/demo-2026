package com.chandler.fcc.server.flow.action.executor;

import com.chandler.fcc.common.dto.command.FNodeDialDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.call.CallSessionManager;
import com.chandler.fcc.server.flow.action.AbstractFccActionExecutor;
import com.chandler.fcc.server.infrastructure.persistence.service.CallFactsQueryService;
import com.chandler.fcc.server.websocket.service.ScreenPopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 外呼坐席动作执行器 (DIAL_AGENT)
 * <p>
 * 向坐席 SIP/WebRTC 终端（如 user/1007）发起呼叫，并将生成的通道 UUID 绑定至当前通话上下文。
 * 坐席终端被真实叫起的同一时刻，向该坐席推送话务弹屏——弹屏与振铃是同一次业务事件的两种表现，
 * 因此这里不再接受外部传入弹屏内容，全部由 {@link ScreenPopService} 依据真实事实装配。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DialAgentActionExecutor extends AbstractFccActionExecutor {

    /**
     * 坐席振铃超时秒数（同时作为弹屏上的振铃倒计时，二者必须一致）
     */
    private static final int AGENT_RING_TIMEOUT_SECONDS = 30;

    private final CallSessionManager sessionManager;
    private final ScreenPopService screenPopService;
    private final CallFactsQueryService callFactsQueryService;

    @Override
    public ActionType getActionType() {
        return ActionType.DIAL_AGENT;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        String agentExt = flowNode.getDataStr("agentExt", "1007");
        String callerNumber = flowNode.getDataStr("callerNumber", "1008");
        String ctrlUuid = flowNode.getDataStr("ctrlUuid", callUuid);
        String agentChannelUuid = flowNode.getDataStr("agentChannelUuid", null);

        // 优先读取 session 中动态绑定的坐席分机 (如 90101 绑定的 1007)
        CallInfoBO session = sessionManager.getByCtrlUuid(ctrlUuid)
                .or(() -> sessionManager.getByCallId(callUuid))
                .orElse(null);
        if (session != null) {
            if (session.getDataStr("agentExt", null) != null && !session.getDataStr("agentExt", "").isBlank()) {
                agentExt = session.getDataStr("agentExt", agentExt);
            } else if (session.getAgentExt() != null && !session.getAgentExt().isBlank()) {
                agentExt = session.getAgentExt();
            }
        }

        if (agentChannelUuid == null || agentChannelUuid.trim().isEmpty()) {
            agentChannelUuid = IdUtil.getUuid();
        }

        // 绑定坐席通道与控制会话
        sessionManager.bindChannel(agentChannelUuid, ctrlUuid);
        final String finalAgentUuid = agentChannelUuid;
        final String finalAgentExt = agentExt;
        String workNo = resolveWorkNo(session, flowNode);
        if (session != null) {
            session.setAgentChannelUuid(finalAgentUuid);
            session.setAgentExt(finalAgentExt);
            session.putData("agentChannelUuid", finalAgentUuid);
            session.putData("agentExt", finalAgentExt);
            if (workNo != null) {
                session.setAgentWorkNo(workNo);
                session.putData("primaryWorkNo", workNo);
                // 坐席姓名同样取自坐席主数据实数，供落库后作为"前序接待人"事实
                callFactsQueryService.findAgentName(workNo)
                        .ifPresent(name -> session.putData("agentName", name));
            }
        }

        log.info("📞 [FCC 执行动作: 呼叫坐席] CallUUID: {}, AgentExt: {}, WorkNo: {}, CtrlUUID: {}, Channel: {}",
                callUuid, agentExt, workNo, ctrlUuid, agentChannelUuid);

        Map<String, String> channelVars = new HashMap<>();
        channelVars.put("hangup_after_bridge", "false");
        channelVars.put("park_after_bridge", "true");
        channelVars.put("absolute_codec_string", "PCMU,PCMA");
        channelVars.put("liberal_dtmf", "true");

        String dialStr = agentExt.contains("/") ? agentExt : "user/" + agentExt;
        FNodeDialDTO.CallParam callParam = FNodeDialDTO.CallParam.builder()
                .dialString(dialStr)
                .cidName("AgentCall")
                .cidNumber(callerNumber)
                .uuid(agentChannelUuid)
                .params(channelVars)
                .build();

        FNodeDialDTO dialDTO = FNodeDialDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(agentChannelUuid)
                .destination(FNodeDialDTO.Destination.builder()
                        .callParams(Collections.singletonList(callParam))
                        .build())
                .timeout(AGENT_RING_TIMEOUT_SECONDS)
                .build();

        FNodeResult result = getFccClient().dial(dialDTO);
        log.info("📥 [FCC 呼叫坐席应答] Result: {}", result);

        // 坐席终端已被真实叫起：立即依据真实事实推送弹屏
        if (session != null) {
            screenPopService.pushForAgentLeg(session, workNo, finalAgentExt, AGENT_RING_TIMEOUT_SECONDS);
        }
    }

    /**
     * 解析本次呼叫的目标坐席工号
     * <p>
     * 依次取：会话已确定的接待坐席 -> 会话上下文中的 primaryWorkNo -> 流程节点显式声明。
     * 三者皆无则返回空，由弹屏服务跳过推送而不是猜测一个工号。
     * </p>
     *
     * @param session  通话会话上下文 (可为 null)
     * @param flowNode 流程节点
     * @return 坐席工号；无法确定时返回 null
     */
    private String resolveWorkNo(CallInfoBO session, FlowNode flowNode) {
        if (session != null) {
            if (session.getAgentWorkNo() != null && !session.getAgentWorkNo().isBlank()) {
                return session.getAgentWorkNo();
            }
            String primaryWorkNo = session.getDataStr("primaryWorkNo", null);
            if (primaryWorkNo != null && !primaryWorkNo.isBlank()) {
                return primaryWorkNo;
            }
        }
        String nodeWorkNo = flowNode.getDataStr("workNo", null);
        return (nodeWorkNo != null && !nodeWorkNo.isBlank()) ? nodeWorkNo : null;
    }
}
