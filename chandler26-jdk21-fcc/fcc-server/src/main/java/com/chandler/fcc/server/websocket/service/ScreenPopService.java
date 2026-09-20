package com.chandler.fcc.server.websocket.service;

import com.chandler.fcc.common.dto.IncomingScreenPopDTO;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.DirectionType;
import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.customer.application.CustomerService;
import com.chandler.fcc.server.infrastructure.persistence.service.CallFactsQueryService;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 坐席话务弹屏服务
 * <p>
 * 唯一的弹屏事实装配入口：以通话上下文 (NATS 通道事件驱动) 为主干，叠加数据库中的
 * 流程配置、坐席主数据与同号码前序通话事实，装配出 {@link IncomingScreenPopDTO} 并推送至坐席终端。
 * </p>
 * <p>
 * <b>真实性约束</b>：所有字段都来自可追溯的事实来源，取不到就留空；本类不做任何示例数据兜底，
 * 也不做无依据的推断（例如不臆造客户等级、企业规模、待办工单等系统内并不存在的档案）。
 * </p>
 *
 * @author Chandler
 * @since 2026-09-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenPopService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AgentWebSocketService agentWebSocketService;
    private final CallFactsQueryService callFactsQueryService;
    private final FlowConfig flowConfig;
    private final CustomerService customers;

    /**
     * 坐席侧通道被真实叫起时推送弹屏
     * <p>
     * 调用时机是控制面确实向该坐席终端下发 Dial 的那一刻，因此坐席听到振铃与看到弹屏是同一事件。
     * </p>
     *
     * @param call              通话上下文
     * @param workNo            目标坐席工号
     * @param agentExt          目标坐席终端分机
     * @param ringTimeoutSeconds 实际下发的振铃超时秒数
     * @return 成功投递的坐席会话数
     */
    public int pushForAgentLeg(CallInfoBO call, String workNo, String agentExt, Integer ringTimeoutSeconds) {
        if (call == null || !StringUtils.hasText(workNo)) {
            log.warn("⚠️ [弹屏] 通话上下文或坐席工号缺失，跳过推送: workNo={}", workNo);
            return 0;
        }
        // 同一通电话同一坐席只弹一次，避免通道状态抖动导致重复弹屏
        if (call.getData().putIfAbsent("screen_pop_pushed", workNo) != null) {
            return 0;
        }
        IncomingScreenPopDTO payload = assemble(call, workNo, agentExt, ringTimeoutSeconds);
        return agentWebSocketService.pushScreenPop(workNo, payload);
    }

    /**
     * 按通话上下文中的坐席信息推送弹屏
     *
     * @param call              通话上下文
     * @param ringTimeoutSeconds 实际下发的振铃超时秒数
     * @return 成功投递的坐席会话数
     */
    public int pushForCall(CallInfoBO call, Integer ringTimeoutSeconds) {
        if (call == null) {
            return 0;
        }
        String workNo = call.getAgentWorkNo() != null ? call.getAgentWorkNo() : call.getDataStr("primaryWorkNo", null);
        String agentExt = call.getAgentExt() != null ? call.getAgentExt() : call.getDataStr("agentExt", null);
        return pushForAgentLeg(call, workNo, agentExt, ringTimeoutSeconds);
    }

    /**
     * 装配坐席弹屏载荷
     * <p>
     * 字段来源逐项对应真实事实：号码/方向来自通话上下文；流程名称来自已发布流程配置；
     * IVR 轨迹来自真实捕获的按键；客户姓名与企业仅使用坐席外呼时录入的值；
     * 前序通话与录音来自 {@code fcc_call_session} / {@code fcc_call_recording}。
     * </p>
     *
     * @param call              通话上下文
     * @param workNo            目标坐席工号
     * @param agentExt          目标坐席终端分机
     * @param ringTimeoutSeconds 实际下发的振铃超时秒数
     * @return 弹屏载荷
     */
    public IncomingScreenPopDTO assemble(CallInfoBO call, String workNo, String agentExt,
                                         Integer ringTimeoutSeconds) {
        boolean inbound = call.getDirection() != DirectionType.OUTBOUND;
        String customerNumber = inbound ? call.getCallerNumber() : call.getDestinationNumber();
        String accessNumber = inbound ? call.getDestinationNumber() : call.getCallerNumber();

        Optional<String> flowNameOpt = flowConfig.getFlowName(call.getModelKey());
        String flowName = flowNameOpt.orElse(null);
        String ivrDigit = call.getDataStr("ivrSelectedDigit", null);

        IncomingScreenPopDTO.IncomingScreenPopDTOBuilder builder = IncomingScreenPopDTO.builder()
                .callId(call.getCallId())
                .direction(inbound ? DirectionType.INBOUND.name() : DirectionType.OUTBOUND.name())
                .callerNumber(customerNumber)
                .didNumber(accessNumber)
                .ringTimeoutSeconds(ringTimeoutSeconds)
                .flowName(flowName)
                .ivrPath(buildIvrPath(ivrDigit, flowName))
                .routingReason(buildRoutingReason(ivrDigit, flowName, workNo, agentExt))
                .customerName(call.getDataStr("customerName", null))
                .companyName(call.getDataStr("companyName", null));

        applyCallerHistory(builder, customerNumber, inbound, call.getCallId(), workNo);
        try {
            customers.match(workNo, customerNumber).ifPresent(customer ->
                builder.customerName(customer.getName()).companyName(customer.getCompanyName())
            );
        } catch (RuntimeException failure) {
            log.warn("[弹屏] 客户查询暂不可用 callId={}", call.getCallId());
        }
        return builder.build();
    }

    /**
     * 叠加同号码前序通话事实 (防撞单)
     *
     * @param builder        载荷构造器
     * @param customerNumber 客户号码
     * @param inbound        是否为呼入
     * @param callId         当前业务通话标识 (用于排除自身)
     * @param workNo         当前收件坐席，仅允许本人历史
     */
    private void applyCallerHistory(IncomingScreenPopDTO.IncomingScreenPopDTOBuilder builder,
                                    String customerNumber, boolean inbound, String callId, String workNo) {
        if (!StringUtils.hasText(customerNumber)) {
            return;
        }
        try {
            Long currentId = CallPersistenceService.parseNumericId(callId);
            callFactsQueryService.findLatestHistory(customerNumber, inbound, currentId, workNo).ifPresent(history -> {
                builder.lastAgentName(history.agentName())
                        .lastAgentWorkNo(history.agentWorkNo())
                        .lastCallTime(formatTime(history.startedAt()))
                        .lastCallSummary(buildCallSummary(history))
                        .lastRecordingUrl(buildRecordingUrl(history));
            });
        } catch (Exception e) {
            // 前序记录只是增强信息，查询失败不影响本次弹屏的主体事实
            log.warn("[弹屏] 装配前序通话事实失败 callId={}", callId);
        }
    }

    /**
     * 构建真实 IVR / 流程轨迹
     *
     * @param ivrDigit 真实捕获的按键值 (可能为空)
     * @param flowName 真实流程名称 (可能为空)
     * @return 轨迹文案；两者都不可得时返回 null
     */
    private String buildIvrPath(String ivrDigit, String flowName) {
        boolean hasDigit = StringUtils.hasText(ivrDigit);
        boolean hasFlow = StringUtils.hasText(flowName);
        if (hasDigit && hasFlow) {
            return "按键[" + ivrDigit + "] → " + flowName;
        }
        if (hasDigit) {
            return "按键[" + ivrDigit + "]";
        }
        return hasFlow ? flowName : null;
    }

    /**
     * 构建真实路由依据
     *
     * @param ivrDigit 真实捕获的按键值
     * @param flowName 真实流程名称
     * @param workNo   目标坐席工号
     * @param agentExt 目标坐席终端分机
     * @return 路由依据文案
     */
    private String buildRoutingReason(String ivrDigit, String flowName, String workNo, String agentExt) {
        StringBuilder reason = new StringBuilder();
        if (StringUtils.hasText(ivrDigit)) {
            reason.append("IVR 按键[").append(ivrDigit).append("] 分流");
        } else if (StringUtils.hasText(flowName)) {
            reason.append(flowName).append(" 直接路由");
        } else {
            reason.append("直接路由");
        }
        if (StringUtils.hasText(workNo)) {
            reason.append(" → 坐席 ").append(workNo);
        }
        if (StringUtils.hasText(agentExt)) {
            reason.append(" · 终端 ").append(agentExt);
        }
        return reason.toString();
    }

    /**
     * 构建前序通话摘要
     *
     * @param history 前序通话事实
     * @return 摘要文案；无时长与结果时返回 null
     */
    private String buildCallSummary(CallFactsQueryService.CallerHistory history) {
        StringBuilder summary = new StringBuilder();
        if (history.talkDurationMs() != null && history.talkDurationMs() > 0) {
            long totalSeconds = history.talkDurationMs() / 1000;
            summary.append(String.format("通话 %02d:%02d", totalSeconds / 60, totalSeconds % 60));
        }
        if (StringUtils.hasText(history.result())) {
            if (summary.length() > 0) {
                summary.append(" · ");
            }
            summary.append(history.result());
        }
        return summary.length() == 0 ? null : summary.toString();
    }

    /**
     * 构建前序通话录音复播地址
     * <p>
     * 仅当数据库确实存在录音元数据时才给出地址，避免前端展示"可播放"的假按钮。
     * 地址指向管理端录音复播接口，该接口以本次通话的数值主键定位共享目录内的文件。
     * </p>
     *
     * @param history 前序通话事实
     * @return 复播地址；无可用录音时返回 null
     */
    private String buildRecordingUrl(CallFactsQueryService.CallerHistory history) {
        if (history.recordingId() == null || history.callId() == null) {
            return null;
        }
        return "/api/admin/recordings/" + history.callId() + "/stream";
    }

    /**
     * 格式化时间
     *
     * @param time 时间
     * @return 格式化文本；入参为空时返回 null
     */
    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }
}
