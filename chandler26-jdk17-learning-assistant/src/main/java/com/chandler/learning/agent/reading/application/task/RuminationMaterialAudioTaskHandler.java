package com.chandler.learning.agent.reading.application.task;

import com.chandler.learning.agent.reading.application.RuminationAudioService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.application.AiTaskExecutionService;
import com.chandler.learning.agent.task.application.contract.AiTaskHandler;
import com.chandler.learning.agent.task.application.contract.AiTaskStepDefinition;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.task.domain.enums.AiTaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 知识反刍场景材料全文语音异步合成任务处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuminationMaterialAudioTaskHandler implements AiTaskHandler {

    private static final String SYNTHESIZE_STEP = "synthesize_rumination_audio";

    private final RuminationAudioService ruminationAudioService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    /** 返回绑定的异步任务类型。 */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.RUMINATION_MATERIAL_AUDIO;
    }

    /** 声明单步语音切片合成步骤定义。 */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(new AiTaskStepDefinition(SYNTHESIZE_STEP, "合成反刍场景文章语音", 10));
    }

    /**
     * 执行反刍材料全文音频合成并推进异步任务完成状态。
     */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long materialId = task.getUnitId();
        if (materialId == null && payload != null && payload.get("materialId") != null) {
            materialId = Long.parseLong(String.valueOf(payload.get("materialId")));
        }
        Long operator = task.getOperatorUserId();
        boolean forceRefresh = payload != null && Boolean.parseBoolean(String.valueOf(payload.get("forceRefresh")));

        log.info("开始执行反刍材料语音生成任务 taskId={} materialId={} operator={} forceRefresh={}",
                task.getId(), materialId, operator, forceRefresh);

        final Long targetMaterialId = materialId;
        executionService.execute(task.getId(), SYNTHESIZE_STEP, operator, null, () -> {
            ruminationAudioService.generateOrGetMaterialAudio(operator, targetMaterialId, forceRefresh);
            return 1;
        });

        taskService.updateProgress(task.getId(), 1, 1, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
        log.info("反刍材料语音生成任务执行成功 taskId={} materialId={}", task.getId(), targetMaterialId);
    }
}
