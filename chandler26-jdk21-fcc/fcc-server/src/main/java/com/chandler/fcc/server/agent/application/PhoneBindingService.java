package com.chandler.fcc.server.agent.application;

import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.infrastructure.PhoneBindingMapper;
import com.chandler.fcc.server.command.FccClient;
import com.chandler.fcc.server.flow.application.SystemFlowRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 处理实体话机拨打绑定号码后输入坐席工号的自助绑定流程。
 *
 * <p>绑定身份来自 Sidecar 上报的节点和 SIP 已认证分机，坐席无需先在 PC 端创建挑战码。
 * 节点、分机和坐席必须都是启用资源，换绑过程在数据库事务中锁定目标行。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneBindingService implements SystemFlowRuntime {

    private static final String BINDING_NUMBER = "0000";
    private static final String TEMPLATE = "PHONE_BINDING";
    private static final String STATE_START = "START";
    private static final String STATE_READY = "READY";
    private static final String DATA_EXTENSION = "bindingExtension";
    private static final String DATA_PROMPT_SENT = "bindingPromptSent";
    private static final String DATA_COMPLETED = "bindingCompleted";
    private static final Set<FlowActionType> SUPPORTED_ACTIONS = Set.of(
        FlowActionType.VALIDATE_BINDING_EXTENSION,
        FlowActionType.READ_DTMF,
        FlowActionType.BIND_AGENT_EXTENSION,
        FlowActionType.HANGUP_BINDING_CHANNEL
    );

    private final PhoneBindingMapper mapper;
    private final TransactionTemplate transactions;
    private final FccClient client;

    @Value("${fcc.binding.prompt-file:}")
    private String promptFile;

    @Value("${fcc.binding.work-no-min-digits:2}")
    private int minWorkNoDigits;

    @Value("${fcc.binding.work-no-max-digits:20}")
    private int maxWorkNoDigits;

    /**
     * 返回话机绑定固定模板代码。
     *
     * @return 话机绑定模板集合
     */
    @Override
    public Set<String> templates() {
        return Set.of(TEMPLATE);
    }

    /**
     * 返回话机绑定服务实际执行的公共动作。
     *
     * @param template 固定模板代码
     * @return 话机绑定动作集合；其他模板返回空集合
     */
    @Override
    public Set<FlowActionType> supportedActions(String template) {
        return TEMPLATE.equals(template) ? SUPPORTED_ACTIONS : Set.of();
    }

    /**
     * 截获绑定号码的通道事件并发起工号收号。
     *
     * @param call 当前通话上下文
     * @param params Sidecar 标准化通道事件
     * @return 当前事件是否属于话机绑定流程
     */
    public boolean channel(CallInfoBO call, JsonNode params) {
        if (!BINDING_NUMBER.equals(call.getDestinationNumber())) {
            return false;
        }
        String state = params.path("state").asText();
        if (STATE_START.equals(state)) {
            initializeBinding(call, params);
        } else if (STATE_READY.equals(state)) {
            requestWorkNo(call);
        }
        return true;
    }

    /**
     * 消费绑定流程收取的完整坐席工号并执行原子换绑。
     *
     * @param call 当前绑定通话
     * @param workNo 话机输入的坐席工号
     * @return 当前按键事件是否属于话机绑定流程
     */
    public boolean digits(CallInfoBO call, String workNo) {
        if (!BINDING_NUMBER.equals(call.getDestinationNumber())) {
            return false;
        }
        if (call.getData().putIfAbsent(DATA_COMPLETED, Boolean.TRUE) != null) {
            return true;
        }
        boolean validWorkNo = workNo != null && workNo.matches(
            "[0-9]{" + minWorkNoDigits + "," + maxWorkNoDigits + "}"
        );
        boolean bound = validWorkNo && Boolean.TRUE.equals(bind(call, workNo));
        client.hangup(
            call.getNodeId(),
            call.getCtrlId(),
            call.getGuestChannelUuid(),
            bound ? "NORMAL_CLEARING" : "CALL_REJECTED"
        );
        if (bound) {
            log.info(
                "[话机绑定] 绑定完成 workNo={} extension={} callId={}",
                workNo,
                call.getDataStr(DATA_EXTENSION, ""),
                call.getCallId()
            );
        } else {
            log.warn(
                "[话机绑定] 绑定被拒绝 nodeId={} extension={} callId={}",
                call.getNodeId(),
                call.getDataStr(DATA_EXTENSION, ""),
                call.getCallId()
            );
        }
        return true;
    }

    /**
     * 从可信事件解析并校验发起绑定的 SIP 分机。
     *
     * @param call 当前通话
     * @param params 通道事件
     */
    private void initializeBinding(CallInfoBO call, JsonNode params) {
        String extension = params.path("params").path("authenticated_extension").asText();
        Map<String, Object> context = extension.matches("[0-9]{2,20}")
            ? mapper.bindingContext(call.getNodeId(), extension)
            : null;
        if (context == null || promptFile.isBlank()) {
            reject(call);
            return;
        }
        call.putData(DATA_EXTENSION, extension);
        call.setModelKey("PHONE_BINDING");
    }

    /**
     * 向话机播放提示并收取完整工号。
     *
     * @param call 当前通话
     */
    private void requestWorkNo(CallInfoBO call) {
        if (!call.getData().containsKey(DATA_EXTENSION) || promptFile.isBlank()) {
            reject(call);
            return;
        }
        if (call.getData().putIfAbsent(DATA_PROMPT_SENT, Boolean.TRUE) != null) {
            return;
        }
        client.readDTMF(
            call.getNodeId(),
            FNodeReadDTMFDTO.builder()
                .ctrlUuid(call.getCtrlId())
                .uuid(call.getGuestChannelUuid())
                .media(MediaInfo.builder().type("FILE").data(promptFile).build())
                .minDigits(minWorkNoDigits)
                .maxDigits(maxWorkNoDigits)
                .tries(1)
                .timeout(60)
                .digitTimeout(10_000)
                .terminators("#")
                .regex("^[0-9]+$")
                .build()
        );
    }

    /**
     * 在事务中锁定节点、分机和坐席后完成换绑。
     *
     * @param call 当前通话
     * @param workNo 目标坐席工号
     * @return 是否绑定成功
     */
    private Boolean bind(CallInfoBO call, String workNo) {
        String extension = call.getDataStr(DATA_EXTENSION, "");
        return transactions.execute(status -> {
            Map<String, Object> target = mapper.lockBindingTarget(call.getNodeId(), extension, workNo);
            if (target == null) {
                return false;
            }
            if (mapper.busy(workNo, extension) > 0) {
                return false;
            }
            mapper.disableBindings(workNo, extension);
            mapper.clearExtensions(workNo, extension);
            mapper.clearAgents(workNo, extension);
            if (
                mapper.bindExtension(workNo, extension) != 1 ||
                mapper.bindAgent(workNo, extension) != 1 ||
                mapper.appendBinding(IdUtil.nextId(), workNo, extension) != 1
            ) {
                throw new IllegalStateException("话机绑定对象在事务中发生变化");
            }
            return true;
        });
    }

    /**
     * 拒绝无法确认节点或 SIP 身份的绑定通话。
     *
     * @param call 当前通话
     */
    private void reject(CallInfoBO call) {
        client.hangup(
            call.getNodeId(),
            call.getCtrlId(),
            call.getGuestChannelUuid(),
            "CALL_REJECTED"
        );
    }
}
