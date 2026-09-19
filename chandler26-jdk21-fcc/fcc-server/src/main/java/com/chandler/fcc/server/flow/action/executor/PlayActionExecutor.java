package com.chandler.fcc.server.flow.action.executor;

import com.chandler.fcc.common.dto.command.FNodePlayDTO;
import com.chandler.fcc.common.dto.command.MediaInfo;
import com.chandler.fcc.common.entity.FlowNode;
import com.chandler.fcc.common.entity.FNodeResult;
import com.chandler.fcc.common.enums.ActionType;
import com.chandler.fcc.server.flow.action.AbstractFccActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 放音语音播报动作执行器 (PLAY)
 * <p>
 * 向指定话道播放本地录音文件或执行动态 TTS 文本语音播报。
 * </p>
 *
 * @author Chandler
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayActionExecutor extends AbstractFccActionExecutor {

    @Override
    public ActionType getActionType() {
        return ActionType.PLAY;
    }

    @Override
    public void execute(String callUuid, String flowUuid, FlowNode flowNode) {
        String targetUuid = flowNode.getDataStr("targetUuid", flowNode.getDataStr("uuidA", callUuid));
        String ctrlUuid = flowNode.getDataStr("ctrlUuid", callUuid);
        String soundFile = flowNode.getDataStr("soundFile", null);
        String prompt = flowNode.getDataStr("prompt", null);

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

        log.info("📢 [FCC 执行动作: 放音播报] CallUUID: {}, TargetUUID: {}, Media: {}",
                callUuid, targetUuid, media.getData());

        FNodePlayDTO playDTO = FNodePlayDTO.builder()
                .ctrlUuid(ctrlUuid)
                .uuid(targetUuid)
                .media(media)
                .build();

        FNodeResult result = getFccClient().play(playDTO);
        log.info("📥 [FCC 放音播报应答] Result: {}", result);
    }
}
