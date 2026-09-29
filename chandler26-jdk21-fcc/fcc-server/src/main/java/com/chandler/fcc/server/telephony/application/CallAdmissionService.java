package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.server.agent.domain.AgentLoginStatus;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.agent.infrastructure.data.AgentRuntimeStateData;
import com.chandler.fcc.server.customer.domain.PhoneNumber;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 呼叫准入控制与安全防盗打检查服务 (Call Admission Control & Anti-Fraud Service).
 *
 * <p>负责在通话进入业务状态机前执行准入过滤与安全防护：
 * 1. 终端分机归属检查 (是否已分配启用坐席)
 * 2. 坐席实时登录态强校验 (未在工作台签到/已退出禁止拨打电话，防止工位话机盗打)
 * 3. 目标号码防盗打风控过滤 (国际长途黑名单、高危声讯/特服号拦截)
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallAdmissionService {

    private final AgentRuntimeMapper agents;

    /**
     * 高危声讯与特服号黑名单前缀
     */
    private static final Set<String> FORBIDDEN_PREFIXES = Set.of(
        "168", // 声讯台高额扣费
        "950", // 境外高额虚拟号
        "96",  // 特殊行业接入号
        "400"  // 400 专线只作呼入客服热线，严禁外呼反拨产生高额账单
    );

    /**
     * 准入决策评估结果
     */
    public record AdmissionDecision(
        boolean admitted,
        String reasonCode,
        String reasonMessage,
        Map<String, Object> agentData,
        String workNo
    ) {
        public static AdmissionDecision grant(Map<String, Object> agentData, String workNo) {
            return new AdmissionDecision(true, "ADMITTED", "准入通过", agentData, workNo);
        }

        public static AdmissionDecision reject(String reasonCode, String reasonMessage) {
            return new AdmissionDecision(false, reasonCode, reasonMessage, null, null);
        }
    }

    /**
     * 校验坐席分机及其在岗登录态。
     *
     * @param extension 已认证终端分机号
     * @return 准入决策
     */
    public AdmissionDecision checkAgentAdmission(String extension) {
        if (extension == null || extension.isBlank()) {
            return AdmissionDecision.reject("UNAUTHENTICATED_EXTENSION", "未提供已认证分机号");
        }
        Map<String, Object> agent = agents.agentByEndpoint(extension);
        if (agent == null || agent.get("workNo") == null) {
            log.warn("[准入拦截] 未知分机或未绑定启用坐席 extension={}", extension);
            return AdmissionDecision.reject("AGENT_NOT_FOUND", "分机未绑定有效坐席工号");
        }
        String workNo = agent.get("workNo").toString();
        AgentRuntimeStateData presence = agents.presence(workNo);
        if (presence == null || presence.getLoginStatus() == null ||
            AgentLoginStatus.LOGOUT.name().equalsIgnoreCase(presence.getLoginStatus())) {
            log.warn("[准入拦截-坐席离线] 坐席未在客户端登录，禁止发起呼叫 workNo={}, extension={}, status={}",
                workNo, extension, presence == null ? "NULL" : presence.getLoginStatus());
            return AdmissionDecision.reject("AGENT_OFFLINE", "坐席处于离线退出状态，禁止发起外呼");
        }
        return AdmissionDecision.grant(agent, workNo);
    }

    /**
     * 校验被叫号码合规性 (防盗打过滤).
     *
     * @param rawNumber 拨打的目标号码
     * @return 准入决策
     */
    public AdmissionDecision checkDestinationRisk(String rawNumber) {
        if (rawNumber == null || rawNumber.isBlank()) {
            return AdmissionDecision.reject("EMPTY_DESTINATION", "目标号码为空");
        }
        String number;
        try {
            number = PhoneNumber.normalize(rawNumber);
        } catch (IllegalArgumentException e) {
            return AdmissionDecision.reject("INVALID_NUMBER_FORMAT", e.getMessage());
        }

        // 1. 国际长途防盗打拦截 (00 开头、+ 开头、011 开头)
        if (number.startsWith("00") || number.startsWith("+") || number.startsWith("011")) {
            log.warn("[防盗打拦截-国际长途] 检测到国际长途号码拨打 rawNumber={}, normalized={}", rawNumber, number);
            return AdmissionDecision.reject("FORBIDDEN_INTERNATIONAL", "系统禁止拨打国际长途号码");
        }

        // 2. 高危声讯及非出局特服号拦截 (168, 950, 96, 400)
        for (String prefix : FORBIDDEN_PREFIXES) {
            if (number.startsWith(prefix)) {
                log.warn("[防盗打拦截-高危特服] 检测到高危或非出局特服号码 rawNumber={}, prefix={}", rawNumber, prefix);
                return AdmissionDecision.reject("FORBIDDEN_PREMIUM_PREFIX", "禁止拨打特服或高危号段: " + prefix);
            }
        }

        return AdmissionDecision.grant(null, null);
    }
}
