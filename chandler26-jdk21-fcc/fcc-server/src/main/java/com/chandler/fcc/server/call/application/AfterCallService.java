package com.chandler.fcc.server.call.application;

import com.chandler.fcc.common.dto.WsMessageDTO;
import com.chandler.fcc.common.enums.WsMessageTypeEnum;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.infrastructure.AfterCallMapper;
import com.chandler.fcc.server.telephony.application.AgentIdentityService;
import com.chandler.fcc.server.websocket.service.AgentWebSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * 保存话后小结并在持久化成功后结束坐席整理态。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AfterCallService {

    private static final Set<String> ALLOWED_INTENTS = Set.of("HIGH", "MID", "LOW", "UNASSESSED");

    private final AfterCallMapper mapper;
    private final AgentRuntimeMapper agents;
    private final AgentIdentityService identity;
    private final AgentWebSocketService websocket;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;

    @Value("${fcc.agent.acw-timeout-seconds:30}")
    private int acwTimeoutSeconds;

    /**
     * 保存当前坐席已结束通话的小结，重复请求保留首次结果。
     *
     * @param callId 通话标识
     * @param category 业务分类
     * @param intent 客户意向等级
     * @param notes 沟通纪要
     * @throws ResponseStatusException 请求非法或通话不存在时抛出
     */
    public void save(String callId, String category, String intent, String notes) {
        validate(category, intent, notes);
        String workNo = identity.requirePrincipal().workNo();
        String payload = serialize(category, intent, notes);

        transactions.executeWithoutResult(status -> {
            if (mapper.lockEnded(workNo, callId) == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "已结束通话不存在");
            }
            if (mapper.save(workNo, callId, payload) == 1) {
                agents.completeAcw(workNo, callId);
            }
        });
        log.info("[话后整理] 小结已保存 workNo={} callId={}", workNo, callId);
    }

    /**
     * 查询当前坐席已保存的话后小结。
     *
     * @param callId 通话标识
     * @return 已保存的小结；未提交时返回空对象
     */
    public Object detail(String callId) {
        String workNo = identity.requirePrincipal().workNo();
        String value = mapper.detail(workNo, callId);
        try {
            return value == null ? Map.of() : objectMapper.readTree(value);
        } catch (Exception failure) {
            throw new IllegalStateException("小结数据损坏", failure);
        }
    }

    /**
     * 校验话后小结字段约束。
     *
     * @param category 业务分类
     * @param intent 客户意向等级
     * @param notes 沟通纪要
     */
    private void validate(String category, String intent, String notes) {
        if (
            category == null || category.isBlank() || category.length() > 64 ||
            !ALLOWED_INTENTS.contains(intent == null ? "" : intent) ||
            notes == null || notes.length() > 2000
        ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请检查小结类别、意向和纪要长度");
        }
    }

    /**
     * 将小结转换为稳定 JSON 载荷。
     *
     * @param category 业务分类
     * @param intent 客户意向等级
     * @param notes 沟通纪要
     * @return JSON 字符串
     */
    private String serialize(String category, String intent, String notes) {
        try {
            return objectMapper.writeValueAsString(
                Map.of(
                    "category", category,
                    "intent", intent,
                    "notes", notes,
                    "submittedAt", Instant.now().toString()
                )
            );
        } catch (Exception failure) {
            throw new IllegalStateException("话后小结序列化失败", failure);
        }
    }

    /**
     * 定期巡检超期未处理的 ACW 坐席，自动置闲并广播态势通知。
     */
    @Scheduled(fixedDelay = 5000)
    public void reapAcwTimeouts() {
        if (acwTimeoutSeconds <= 0) return;
        List<Map<String, Object>> expired = agents.findExpiredAcwAgents(acwTimeoutSeconds);
        if (expired == null || expired.isEmpty()) return;
        int count = agents.expireAcw(acwTimeoutSeconds);
        if (count > 0) {
            log.info("[话后整理] 超时自动置闲: 清理了 {} 个超期 ACW 坐席 (阈值 {}s)", count, acwTimeoutSeconds);
            for (Map<String, Object> agent : expired) {
                String workNo = (String) agent.get("workNo");
                websocket.sendToWorkNo(
                    workNo,
                    WsMessageDTO.of(
                        WsMessageTypeEnum.AGENT_PRESENCE_CHANGE.getCode(),
                        workNo,
                        null,
                        Map.of("workStatus", "READY", "reason", "ACW_TIMEOUT")
                    )
                );
            }
        }
    }
}
