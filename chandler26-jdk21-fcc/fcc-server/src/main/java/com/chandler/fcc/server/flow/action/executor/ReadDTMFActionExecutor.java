package com.chandler.fcc.server.flow.action.executor;

import com.chandler.fcc.common.dto.command.FNodeReadDTMFDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.server.flow.action.AbstractFccActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 按键收号动作执行器 (READ_DTMF)
 * <p>
 * 向指定话道播放导航/提示音并收集客户输入的电话按键，支持设置有效字符正则、最大重试及超时。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadDTMFActionExecutor extends AbstractFccActionExecutor {

    /**
     * 返回执行器支持的动作类型。
     * @return 业务动作类型
     */
    @Override
    public ActionType getActionType() {
        return ActionType.READ_DTMF;
    }

    /**
     * 根据明确的运行时标识执行呼叫动作。
     * @param callUuid 业务通话标识，不作为话道或控制标识
     * @param flowUuid 流程步骤实例标识
     * @param flowNode 包含独立控制、话道及动作参数的节点
     * @throws IllegalArgumentException 必需参数缺失时抛出
     */
    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        String targetUuid = requiredData(flowNode, "channelUuid");
        String ctrlUuid = requiredData(flowNode, "ctrlId");
        String soundFile = flowNode.getDataStr("soundFile", null);
        String prompt = flowNode.getDataStr("prompt", null);
        String thankYouFile = flowNode.getDataStr("thankYouFile", null);
        String regex = flowNode.getDataStr("regex", "[0-9#*]");
        String actionAfter = flowNode.getDataStr("actionAfter", "park");

        int tries = 2;
        try {
            tries = Integer.parseInt(flowNode.getDataStr("tries", "2"));
        } catch (NumberFormatException ignored) {}

        int timeout = 5000;
        try {
            timeout = Integer.parseInt(flowNode.getDataStr("timeout", "5000"));
        } catch (NumberFormatException ignored) {}

        MediaInfo media;
        if (soundFile != null && !soundFile.isEmpty()) {
            media = MediaInfo.builder()
                    .type("FILE")
                    .data(soundFile)
                    .build();
        } else {
            media = MediaInfo.builder()
                    .type("TEXT")
                    .data(prompt != null ? prompt : "")
                    .voice("aiqi")
                    .engine("ali")
                    .build();
        }

        log.info("🔢 [FCC 执行动作: 按键收号] CallUUID: {}, TargetUUID: {}, Media: {}, Regex: {}, Tries: {}",
                callUuid, targetUuid, media.getData(), regex, tries);

        FNodeReadDTMFDTO readDTO = FNodeReadDTMFDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(targetUuid)
                .media(media)
                .minDigits(1)
                .maxDigits(1)
                .tries(tries)
                .timeout(timeout / 1000)
                .digitTimeout(2000)
                .terminators("#")
                .thankYouFile(thankYouFile)
                .regex(regex)
                .actionAfter(actionAfter)
                .build();

        FNodeResult result = getFccClient().readDTMF(readDTO);
        log.info("📥 [FCC 按键收号应答] Result: {}", result);
    }
}
