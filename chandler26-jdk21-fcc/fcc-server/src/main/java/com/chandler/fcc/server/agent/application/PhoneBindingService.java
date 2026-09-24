package com.chandler.fcc.server.agent.application;

import com.chandler.fcc.common.dto.command.FNodeAnswerDTO;
import com.chandler.fcc.common.dto.command.FNodeHangupDTO;
import com.chandler.fcc.common.dto.command.FNodePlayDTO;
import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.ChannelEventState;
import com.chandler.fcc.common.protocol.FNodeDtmfPostAction;
import com.chandler.fcc.common.protocol.FNodeMediaType;
import com.chandler.fcc.common.protocol.FNodePlayPostAction;
import com.chandler.fcc.common.protocol.FccCommandResultStatus;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.protocol.FccEventParameter;
import com.chandler.fcc.common.protocol.FccFlowEntry;
import com.chandler.fcc.common.util.IdUtil;
import com.chandler.fcc.server.agent.infrastructure.PhoneBindingMapper;
import com.chandler.fcc.server.flow.FlowConfig;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.flow.application.SystemFlowRuntime;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>绑定入口来自 FreeSWITCH dialplan 标记，身份来自 Sidecar 上报的 SIP 已认证分机，
 * 坐席无需先在 PC 端创建挑战码。分机和坐席必须都是启用资源，换绑过程在数据库事务中锁定目标行。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneBindingService implements SystemFlowRuntime {

    private static final String BINDING_NUMBER = "0000";
    private static final String TEMPLATE = "PHONE_BINDING";
    private static final String DATA_EXTENSION = "bindingExtension";
    private static final String DATA_INITIALIZED = "bindingInitialized";
    private static final String DATA_ANSWER_REQUESTED = "bindingAnswerRequested";
    private static final String DATA_ANSWER_ACCEPTED = "bindingAnswerAccepted";
    private static final String DATA_PROMPT_TEXT = "bindingPromptText";
    private static final String DATA_SUCCESS_TEXT = "bindingSuccessText";
    private static final String DATA_FAILURE_TEXT = "bindingFailureText";
    private static final String DATA_PROMPT_REQUESTED = "bindingPromptRequested";
    private static final String DATA_PROMPT_SENT = "bindingPromptSent";
    private static final String DATA_COMPLETED = "bindingCompleted";
    private static final String DATA_RESULT_REQUESTED = "bindingResultRequested";
    private static final String DATA_RESULT_SENT = "bindingResultSent";
    private static final String DATA_HANGUP_REQUESTED = "bindingHangupRequested";
    private static final String DATA_REJECTED = "bindingRejected";
    private static final Set<FlowActionType> SUPPORTED_ACTIONS = Set.of(
        FlowActionType.VALIDATE_BINDING_EXTENSION,
        FlowActionType.ANSWER_BINDING_CHANNEL,
        FlowActionType.READ_DTMF,
        FlowActionType.BIND_AGENT_EXTENSION,
        FlowActionType.PLAY_BINDING_RESULT,
        FlowActionType.HANGUP_BINDING_CHANNEL
    );

    private final PhoneBindingMapper mapper;
    private final TransactionTemplate transactions;
    private final FlowActionExecutionService flowActions;
    private final CallPersistenceService persistence;
    private final FlowConfig flowConfig;
    private final ObjectMapper objectMapper;

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
        synchronized (call) {
            ChannelEventState state = ChannelEventState.fromWireValue(
                params.path(FccEventField.STATE.getWireName()).asText()
            );
            if (state == ChannelEventState.READY) {
                if (!call.getData().containsKey(DATA_INITIALIZED)) {
                    flowActions.executeInternal(
                        call,
                        FlowActionType.VALIDATE_BINDING_EXTENSION,
                        () -> initializeBinding(call, params)
                    );
                }
                if (call.getData().containsKey(DATA_INITIALIZED)) {
                    requestAnswer(call);
                }
            } else if (
                state == ChannelEventState.ANSWERED &&
                call.getData().containsKey(DATA_ANSWER_REQUESTED)
            ) {
                requestWorkNo(call);
            } else if (
                state == ChannelEventState.DESTROY &&
                call.getData().containsKey(DATA_INITIALIZED)
            ) {
                call.putData("terminal", Boolean.TRUE);
                persistence.saveOrUpdateSession(call);
            }
        }
        return true;
    }

    /**
     * 消费与话机绑定相关的异步指令最终结果。
     *
     * <p>收号结果来自 {@code FNode.ReadDTMF} 的 {@code Event.CommandResult}；
     * 绑定结果语音播放完成后，再单独下发挂机指令。逐键 DTMF 和 READY 状态均不能
     * 代替这两个命令完成事实。</p>
     *
     * @param call 当前绑定通话
     * @param params Sidecar 规范指令结果参数
     * @return 当前结果是否属于话机绑定流程
     */
    public boolean commandResult(CallInfoBO call, JsonNode params) {
        if (!BINDING_NUMBER.equals(call.getDestinationNumber())) {
            return false;
        }
        synchronized (call) {
            String commandId = params.path(FccEventField.COMMAND_ID.getWireName()).asText();
            FccCommandResultStatus status = FccCommandResultStatus.fromWireValue(
                params.path(FccEventField.COMMAND_STATUS.getWireName()).asText()
            );
            if (("binding-dtmf-" + call.getCallId()).equals(commandId)) {
                if (call.getData().containsKey(DATA_HANGUP_REQUESTED)) {
                    return true;
                }
                if (status != FccCommandResultStatus.SUCCEEDED) {
                    requestHangup(call, "NORMAL_TEMPORARY_FAILURE");
                    return true;
                }
                completeBinding(
                    call,
                    params.path(FccEventField.RESULT.getWireName()).path("dtmf").asText(null)
                );
                return true;
            }
            if (("binding-result-" + call.getCallId()).equals(commandId)) {
                requestHangup(
                    call,
                    status == FccCommandResultStatus.SUCCEEDED
                        ? "NORMAL_CLEARING"
                        : "NORMAL_TEMPORARY_FAILURE"
                );
                return true;
            }
        }
        return true;
    }

    /**
     * 完成一次收号结果的校验、原子换绑和结果播报。
     *
     * @param call 当前绑定通话
     * @param workNo ReadDTMF 返回的完整坐席工号
     */
    private void completeBinding(CallInfoBO call, String workNo) {
        if (
            !call.getData().containsKey(DATA_INITIALIZED) ||
            !call.getData().containsKey(DATA_PROMPT_REQUESTED)
        ) {
            return;
        }
        if (call.getData().containsKey(DATA_COMPLETED)) {
            playBindingResult(
                call,
                Boolean.TRUE.equals(call.getData().get("bindingAccepted"))
            );
            return;
        }
        boolean validWorkNo = workNo != null && workNo.matches(
            "[0-9]{" + minWorkNoDigits + "," + maxWorkNoDigits + "}"
        );
        boolean bound = validWorkNo && Boolean.TRUE.equals(
            flowActions.executeInternal(
                call,
                FlowActionType.BIND_AGENT_EXTENSION,
                () -> bind(call, workNo)
            ).getOutput()
        );
        call.putData(DATA_COMPLETED, Boolean.TRUE);
        call.putData("bindingAccepted", bound);
        persistence.saveOrUpdateSession(call);
        playBindingResult(call, bound);
        if (bound) {
            log.info(
                "[话机绑定] 绑定完成 workNo={} extension={} callId={}",
                workNo,
                call.getDataStr(DATA_EXTENSION, ""),
                call.getCallId()
            );
        } else {
            log.warn(
                "[话机绑定] 绑定被拒绝 extension={} callId={}",
                call.getDataStr(DATA_EXTENSION, ""),
                call.getCallId()
            );
        }
    }

    /**
     * 从可信事件解析并校验发起绑定的 SIP 分机。
     *
     * @param call 当前通话
     * @param params 通道事件
     * @return 是否通过可信入口、认证分机和发布流程校验
     */
    private Boolean initializeBinding(CallInfoBO call, JsonNode params) {
        JsonNode eventParameters = params.path(FccEventField.PARAMETERS.getWireName());
        String flowEntry = eventParameters
            .path(FccEventParameter.FLOW_ENTRY.getWireName())
            .asText();
        if (!FccFlowEntry.PHONE_BINDING.getWireValue().equals(flowEntry)) {
            rejectUntrusted(call, "MISSING_TRUSTED_FLOW_ENTRY");
            return false;
        }
        String extension = eventParameters
            .path(FccEventParameter.AUTHENTICATED_EXTENSION.getWireName())
            .asText();
        Map<String, Object> context = extension.matches("[0-9]{2,20}")
            ? mapper.bindingContext(extension)
            : null;
        if (context == null) {
            rejectUntrusted(call, "INVALID_AUTHENTICATED_EXTENSION");
            return false;
        }
        FlowConfig.FlowSnapshot flow = flowConfig.getPublishedFlow(null, TEMPLATE).orElse(null);
        if (flow == null) {
            rejectUntrusted(call, "PHONE_BINDING_FLOW_NOT_PUBLISHED");
            return false;
        }
        if (!configureMediaText(call, flow.definitionJson())) {
            rejectUntrusted(call, "PHONE_BINDING_FLOW_INVALID");
            return false;
        }
        call.putData(DATA_EXTENSION, extension);
        call.putData(DATA_INITIALIZED, Boolean.TRUE);
        call.setModelKey("PHONE_BINDING");
        call.putData("runtimeTemplate", TEMPLATE);
        call.putData("flowDefinitionId", flow.definitionId());
        call.putData("flowVersionId", flow.versionId());
        persistence.saveOrUpdateSession(call);
        return true;
    }

    /**
     * 请求 Sidecar 应答绑定话道，后续等待真实 {@code ANSWERED} 事件。
     *
     * @param call 当前绑定通话
     */
    private void requestAnswer(CallInfoBO call) {
        if (call.getData().containsKey(DATA_ANSWER_ACCEPTED)) {
            return;
        }
        if (!call.getData().containsKey(DATA_ANSWER_REQUESTED)) {
            call.putData(DATA_ANSWER_REQUESTED, Boolean.TRUE);
            persistence.saveOrUpdateSession(call);
        }
        flowActions.executeFNode(
            call,
            FlowActionType.ANSWER_BINDING_CHANNEL,
            FNodeAnswerDTO.builder()
                .ctrlUuid(call.getCtrlId())
                .uuid(call.getGuestChannelUuid())
                .build(),
            "binding-answer-" + call.getCallId()
        );
        call.putData(DATA_ANSWER_ACCEPTED, Boolean.TRUE);
        persistence.saveOrUpdateSession(call);
    }

    /**
     * 向话机播放提示并收取完整工号。
     *
     * @param call 当前通话
     */
    private void requestWorkNo(CallInfoBO call) {
        if (!call.getData().containsKey(DATA_INITIALIZED)) {
            return;
        }
        if (call.getData().containsKey(DATA_PROMPT_SENT)) {
            return;
        }
        if (!call.getData().containsKey(DATA_PROMPT_REQUESTED)) {
            call.putData(DATA_PROMPT_REQUESTED, Boolean.TRUE);
            persistence.saveOrUpdateSession(call);
        }
        flowActions.executeFNode(
            call,
            FlowActionType.READ_DTMF,
            FNodeReadDTMFDTO.builder()
                .ctrlUuid(call.getCtrlId())
                .uuid(call.getGuestChannelUuid())
                .media(
                    MediaInfo.builder()
                        .type(FNodeMediaType.TEXT)
                        .data(call.getDataStr(DATA_PROMPT_TEXT, ""))
                        .build()
                )
                .minDigits(minWorkNoDigits)
                .maxDigits(maxWorkNoDigits)
                .tries(1)
                .timeout(60_000)
                .digitTimeout(10_000)
                .terminators("#")
                .regex("^[0-9]+$")
                .actionAfter(FNodeDtmfPostAction.PARK)
                .build(),
            "binding-dtmf-" + call.getCallId()
        );
        call.putData(DATA_PROMPT_SENT, Boolean.TRUE);
        persistence.saveOrUpdateSession(call);
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
            Map<String, Object> target = mapper.lockBindingTarget(extension, workNo);
            if (target == null) {
                return false;
            }
            if (mapper.busy(workNo, extension) > 0) {
                return false;
            }
            Long oldBindingId = target.get("activeBindingId") instanceof Number value
                ? value.longValue()
                : null;
            long newBindingId = IdUtil.nextId();
            mapper.deactivateCurrent(workNo);
            mapper.disableBindings(workNo, extension);
            mapper.clearExtensions(workNo, extension);
            if (
                mapper.bindExtension(workNo, extension) != 1 ||
                mapper.appendBinding(newBindingId, workNo, extension) != 1 ||
                mapper.appendSelectionAudit(
                    IdUtil.nextId(),
                    workNo,
                    oldBindingId,
                    newBindingId
                ) != 1
            ) {
                throw new IllegalStateException("话机绑定对象在事务中发生变化");
            }
            return true;
        });
    }

    /**
     * 播报明确的绑定结果，播放完成结果到达后再单独挂机。
     *
     * @param call 当前绑定通话
     * @param bound 是否绑定成功
     */
    private void playBindingResult(CallInfoBO call, boolean bound) {
        if (call.getData().containsKey(DATA_RESULT_SENT)) {
            return;
        }
        if (!call.getData().containsKey(DATA_RESULT_REQUESTED)) {
            call.putData(DATA_RESULT_REQUESTED, Boolean.TRUE);
            persistence.saveOrUpdateSession(call);
        }
        flowActions.executeFNode(
            call,
            FlowActionType.PLAY_BINDING_RESULT,
            FNodePlayDTO.builder()
                .ctrlUuid(call.getCtrlId())
                .uuid(call.getGuestChannelUuid())
                .media(
                    MediaInfo.builder()
                        .type(FNodeMediaType.TEXT)
                        .data(
                            call.getDataStr(
                                bound ? DATA_SUCCESS_TEXT : DATA_FAILURE_TEXT,
                                ""
                            )
                        )
                        .build()
                )
                .actionAfter(FNodePlayPostAction.PARK)
                .build(),
            "binding-result-" + call.getCallId()
        );
        call.putData(DATA_RESULT_SENT, Boolean.TRUE);
        persistence.saveOrUpdateSession(call);
    }

    /**
     * 幂等下发绑定通话挂机指令。
     *
     * @param call 当前绑定通话
     * @param cause 挂机原因
     */
    private void requestHangup(CallInfoBO call, String cause) {
        if (call.getData().containsKey(DATA_HANGUP_REQUESTED)) {
            return;
        }
        flowActions.executeFNode(
            call,
            FlowActionType.HANGUP_BINDING_CHANNEL,
            FNodeHangupDTO.builder()
                .ctrlUuid(call.getCtrlId())
                .uuid(call.getGuestChannelUuid())
                .cause(cause)
                .build(),
            "binding-hangup-" + call.getCallId()
        );
        call.putData(DATA_HANGUP_REQUESTED, Boolean.TRUE);
        persistence.saveOrUpdateSession(call);
    }

    /**
     * 从已发布固定模型读取绑定提示文案，避免 Java 配置音频路径或复制模型内容。
     *
     * @param call 当前绑定通话
     * @param definitionJson 已发布模型 JSON
     * @return 三段业务文案均存在时返回 {@code true}
     */
    private boolean configureMediaText(CallInfoBO call, String definitionJson) {
        try {
            JsonNode definition = objectMapper.readTree(definitionJson);
            JsonNode parameters = definition.path("parameters");
            String promptText = parameters.path("promptText").asText().trim();
            JsonNode resultParameters = null;
            for (JsonNode node : definition.path("nodes")) {
                if ("RESULT".equals(node.path("key").asText())) {
                    resultParameters = node.path("parameters");
                    break;
                }
            }
            String successText = resultParameters == null
                ? ""
                : resultParameters.path("successText").asText().trim();
            String failureText = resultParameters == null
                ? ""
                : resultParameters.path("failureText").asText().trim();
            if (promptText.isBlank() || successText.isBlank() || failureText.isBlank()) {
                return false;
            }
            call.putData(DATA_PROMPT_TEXT, promptText);
            call.putData(DATA_SUCCESS_TEXT, successText);
            call.putData(DATA_FAILURE_TEXT, failureText);
            return true;
        } catch (Exception invalidDefinition) {
            return false;
        }
    }

    /**
     * 拒绝未通过拨号计划入口或 SIP 身份校验的通话，不创建绑定流程事实。
     *
     * @param call 当前通话
     * @param reason 可审计但不包含敏感数据的拒绝原因
     */
    private void rejectUntrusted(CallInfoBO call, String reason) {
        if (call.getData().containsKey(DATA_REJECTED)) {
            return;
        }
        flowActions.executeFNode(
            call,
            FlowActionType.HANGUP_BINDING_CHANNEL,
            FNodeHangupDTO.builder()
                .ctrlUuid(call.getCtrlId())
                .uuid(call.getGuestChannelUuid())
                .cause("CALL_REJECTED")
                .build(),
            "binding-hangup-" + call.getCallId()
        );
        call.putData(DATA_REJECTED, Boolean.TRUE);
        call.putData("terminal", Boolean.TRUE);
        log.warn(
            "[话机绑定] 拒绝未通过入口校验的通话 reason={} callId={} channelUuid={}",
            reason,
            call.getCallId(),
            call.getGuestChannelUuid()
        );
    }
}
