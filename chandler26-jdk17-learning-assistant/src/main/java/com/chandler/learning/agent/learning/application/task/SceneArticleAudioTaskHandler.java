package com.chandler.learning.agent.learning.application.task;

import com.chandler.learning.agent.learning.application.SceneArticleAudioService;
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

/** 场景文章语音分步生成工作流。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SceneArticleAudioTaskHandler implements AiTaskHandler {

    private static final String SYNTHESIZE_STEP = "synthesize_audio";

    private final SceneArticleAudioService sceneArticleAudioService;
    private final AiTaskExecutionService executionService;
    private final AiAsyncTaskService taskService;

    /**
     * 绑定的 AI 异步任务类型：场景文章语音生成。
     */
    @Override
    public AiTaskType taskType() {
        return AiTaskType.SCENE_ARTICLE_AUDIO;
    }

    /**
     * 场景文章语音任务由单步语音切片合成与持久化构成。
     */
    @Override
    public List<AiTaskStepDefinition> steps() {
        return List.of(new AiTaskStepDefinition(SYNTHESIZE_STEP, "合成场景文章语音", 10));
    }

    /**
     * 执行场景文章音频合成任务并更新任务进度与最终完成状态。
     */
    @Override
    public void execute(AiAsyncTask task, Map<String, Object> payload) {
        Long unitId = task.getUnitId();
        Long operator = task.getOperatorUserId();
        boolean forceRefresh = payload != null && Boolean.parseBoolean(String.valueOf(payload.get("forceRefresh")));

        log.info("开始执行场景文章语音任务 taskId={} unitId={} operator={} forceRefresh={}",
                task.getId(), unitId, operator, forceRefresh);

        executionService.execute(task.getId(), SYNTHESIZE_STEP, operator, null, () -> {
            sceneArticleAudioService.generateOrGetSceneAudio(unitId, forceRefresh);
            return 1;
        });

        taskService.updateProgress(task.getId(), 1, 1, 0);
        taskService.complete(task.getId(), AiTaskConstants.STATUS_COMPLETED, null);
        log.info("场景文章语音任务执行成功 taskId={} unitId={}", task.getId(), unitId);
    }
}
