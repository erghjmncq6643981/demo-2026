package com.chandler.fcc.server.telephony.application;

import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.chandler.fcc.common.entity.CallInfoBO;
import com.chandler.fcc.common.enums.FlowActionType;
import com.chandler.fcc.common.protocol.FNodeDtmfPostAction;
import com.chandler.fcc.common.protocol.FNodeMediaType;
import com.chandler.fcc.common.protocol.FccEventField;
import com.chandler.fcc.common.protocol.FccCommandResultStatus;
import com.chandler.fcc.common.protocol.FlowDefinitionValidator;
import com.chandler.fcc.server.flow.application.FlowActionExecutionService;
import com.chandler.fcc.server.infrastructure.persistence.service.CallPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 固定放音收号和 if/else 分支；等待真实按键，超时由呼入调度统一处理。
 */
@Service
@RequiredArgsConstructor
public class InboundMenuService {

    private final FlowActionExecutionService actions;
    private final CallPersistenceService persistence;

    /**
     * 固定当前发布版本的全部参数，后续重载不改变存量通话。
     *
     * @param call 通话
     * @param definition 版本定义
     */
    public void configure(CallInfoBO call, String definition) {
        JsonNode root = FlowDefinitionValidator.validate(definition);
        call.putData("ivrDefinition", root.toString());
        call.putData("ivrMenuEnabled", root.path("menu").path("enabled").asBoolean());
        call.putData("ivrTimeoutAction", root.path("timeoutAction").asText());
        target(call, root.path("defaultRoute"));
    }

    /**
     * 客户驻留后判断 true/false 分支；启用菜单时发送一次稳定命令并等待事件。
     *
     * @param call 通话
     * @return 是否正在等待菜单
     */
    public boolean ready(CallInfoBO call) {
        if (!Boolean.TRUE.equals(call.getData().get("ivrMenuEnabled"))) return false;
        if (call.getData().containsKey("ivrResolved")) return false;
        if (call.getData().containsKey("ivrWaiting")) return true;
        var menu = definition(call).path("menu");
        call.putData("ivrWaiting", true);
        call.putData("flowBranch", "menu.enabled=true");
        long waitSeconds = menu.path("timeoutSeconds").asLong(3);
        if (waitSeconds <= 0) waitSeconds = 3;
        int maxTries = menu.path("maxTries").asInt(3);
        if (maxTries <= 0) maxTries = 3;
        // 3次放音(每次约8s)+3次按键等待(每次3s)+网络与TTS缓冲，Watchdog 时限设为 65 秒，避免提前掐断
        call.putData("ivrDeadline", System.currentTimeMillis() + Math.max(65_000L, (maxTries * 12 + 30) * 1000L));
        String command = "ivr-menu-" + call.getCallId();
        call.putData("flowCommandId", command);
        persistence.saveOrUpdateSession(call);
        actions.executeFNode(
            call,
            FlowActionType.READ_DTMF,
            FNodeReadDTMFDTO.builder()
                    .ctrlUuid(call.getCtrlId())
                    .uuid(call.getGuestChannelUuid())
                    .minDigits(1)
                    .maxDigits(1)
                    .tries(maxTries)
                    .timeout((int) (waitSeconds * 1_000))
                    .digitTimeout(1000)
                    .terminators("#")
                    .regex("[0-9]")
                    .media(MediaInfo.builder()
                        .type(mediaType(menu.path("prompt").asText()))
                        .data(menu.path("prompt").asText())
                        .build())
                    .actionAfter(FNodeDtmfPostAction.PARK)
                    .build(),
            command
        );
        return true;
    }

    /**
     * 使用 ReadDTMF 的最终指令结果解析菜单分支。
     *
     * @param call 当前呼入通话
     * @param params 规范指令结果参数
     * @return 是否命中当前菜单收号指令
     */
    public boolean commandResult(CallInfoBO call, JsonNode params) {
        String commandId = params.path(FccEventField.COMMAND_ID.getWireName()).asText();
        if (!("ivr-menu-" + call.getCallId()).equals(commandId)) {
            return false;
        }
        if (
            call.getData().containsKey("ivrResolved") ||
            call.getData().containsKey("ivrMenuFailed")
        ) {
            return true;
        }
        if (FccCommandResultStatus.fromWireValue(
            params.path(FccEventField.COMMAND_STATUS.getWireName()).asText()
        ) != FccCommandResultStatus.SUCCEEDED) {
            call.getData().remove("ivrWaiting");
            call.putData("ivrMenuFailed", true);
            call.putData("flowBranch", "menu.command-failed");
            persistence.saveOrUpdateSession(call);
            return true;
        }
        String digit = params
            .path(FccEventField.RESULT.getWireName())
            .path("dtmf")
            .asText();
        if (!digit.matches("[0-9]")) {
            call.getData().remove("ivrWaiting");
            call.putData("ivrMenuFailed", true);
            call.putData("flowBranch", "menu.no-digit");
            persistence.saveOrUpdateSession(call);
            return true;
        }
        actions.executeInternal(
            call,
            FlowActionType.SELECT_DIGIT_ROUTE,
            () -> selectRoute(call, digit)
        );
        return true;
    }

    /**
     * 选择按键分支或默认路由并保存决策事实。
     *
     * @param call 当前呼入通话
     * @param digit 已校验的单位按键
     * @return 命中的分支标识
     */
    private String selectRoute(CallInfoBO call, String digit) {
        JsonNode root = definition(call),
            selected = root.path("defaultRoute");
        String branch = "else";
        for (JsonNode candidate : root.path("branches"))
            if (digit.equals(candidate.path("digit").asText())) {
                selected = candidate;
                branch = "digit=" + digit;
                break;
            }
        call.getData().remove("ivrWaiting");
        call.putData("ivrResolved", true);
        call.putData("flowBranch", branch);
        call.putData("ivrBranchPending", true);
        target(call, selected);
        persistence.saveOrUpdateSession(call);
        call.getData().remove("ivrBranchPending");
        persistence.saveOrUpdateSession(call);
        return branch;
    }

    /**
     * 应用受控目标和队列时限。
     *
     * @param call 通话
     * @param route 路由参数
     */
    private void target(CallInfoBO call, JsonNode route) {
        call.getData().remove("directOwner");
        call.getData().remove("groupCode");
        call.putData(
            "AGENT".equals(route.path("targetType").asText()) ? "directOwner" : "groupCode",
            route.path("target").asText()
        );
        call.putData(
            "queueDeadline",
            System.currentTimeMillis() + route.path("queueSeconds").asLong() * 1000
        );
    }

    /**
     * 从持久上下文读取固定定义。
     *
     * @param call 通话
     * @return 已验证定义
     */
    private JsonNode definition(CallInfoBO call) {
        return FlowDefinitionValidator.validate(call.getDataStr("ivrDefinition", ""));
    }

    /**
     * 识别预置音频或导航文案。绝对路径由 FreeSWITCH 直接读取，其余内容交给 Sidecar TTS。
     *
     * @param prompt 流程中的导航提示
     * @return 规范媒体类型
     */
    private FNodeMediaType mediaType(String prompt) {
        return prompt != null && prompt.startsWith("/")
            ? FNodeMediaType.FILE
            : FNodeMediaType.TEXT;
    }
}
