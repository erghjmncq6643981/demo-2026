package com.chandler.learning.agent.reading.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.application.SceneArticleAudioService;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialAudioStatusResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationMaterialMapper;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 知识反刍场景材料全文 AI 语音合成、缓存读取与异步任务管理服务。
 * <p>复用场景学习的阿里云 NLS 分句合成内核，将音频缓存按当前材料 ID 隔离存储并校验归属用户。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuminationAudioService {

    private static final String AUDIO_CATEGORY = "rumination";

    private final LearningRuminationMaterialMapper materialMapper;
    private final SceneArticleAudioService sceneArticleAudioService;
    private final AiAsyncTaskService aiAsyncTaskService;

    /**
     * 查询当前用户指定场景材料已缓存的 MP3 音频流资源（不存在时返回 null）。
     */
    public Resource getExistingMaterialAudio(Long userId, Long materialId) {
        requireOwnedMaterial(userId, materialId);
        return sceneArticleAudioService.getExistingStoredAudio(AUDIO_CATEGORY, materialId);
    }

    /**
     * 清理指定场景材料已缓存的音频文件（覆盖重新生成后使旧音频失效）。
     */
    public void invalidateStoredAudio(Long materialId) {
        if (materialId == null) {
            return;
        }
        sceneArticleAudioService.deleteStoredAudio(AUDIO_CATEGORY, materialId);
    }

    /**
     * 查询指定场景材料的音频就绪状态与异步合成任务进度。
     */
    public RuminationMaterialAudioStatusResponse getAudioStatus(Long userId, Long materialId) {
        LearningRuminationMaterial material = requireOwnedMaterial(userId, materialId);
        boolean hasFile = sceneArticleAudioService.getExistingStoredAudio(AUDIO_CATEGORY, materialId) != null;

        RuminationMaterialAudioStatusResponse response = new RuminationMaterialAudioStatusResponse();
        response.setMaterialId(String.valueOf(materialId));
        response.setHasAudio(hasFile);

        String idempotencyKey = buildIdempotencyKey(materialId);
        AiAsyncTask activeTask = aiAsyncTaskService.findActiveByKey(
                userId, AiTaskConstants.TYPE_RUMINATION_MATERIAL_AUDIO, material.getWordbookId(), idempotencyKey);
        if (activeTask == null) {
            activeTask = aiAsyncTaskService.findActiveByKey(
                    userId, AiTaskConstants.TYPE_RUMINATION_MATERIAL_AUDIO, null, idempotencyKey);
        }

        if (activeTask != null) {
            response.setTaskStatus(activeTask.getStatus());
            response.setAudioStatus(activeTask.getStatus());
            response.setTaskId(String.valueOf(activeTask.getId()));
            response.setErrorMessage(activeTask.getErrorMessage());
        } else if (hasFile) {
            response.setTaskStatus(AiTaskConstants.STATUS_COMPLETED);
            response.setAudioStatus(AiTaskConstants.STATUS_COMPLETED);
        } else {
            String persistedStatus = StringUtils.hasText(material.getAudioStatus()) ? material.getAudioStatus() : "none";
            response.setTaskStatus("failed".equalsIgnoreCase(persistedStatus) ? AiTaskConstants.STATUS_FAILED : "none");
            response.setAudioStatus(persistedStatus);
        }
        return response;
    }

    /**
     * 异步提交知识反刍场景材料全文语音生成任务。
     */
    public RuminationMaterialAudioStatusResponse submitAudioGenerationTask(Long userId, Long materialId, boolean forceRefresh) {
        LearningRuminationMaterial material = requireOwnedMaterial(userId, materialId);
        if (!StringUtils.hasText(material.getArticleContent())) {
            throw LearningAssistantException.badRequest(LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "场景材料英文正文为空，无法生成语音");
        }

        boolean hasFile = sceneArticleAudioService.getExistingStoredAudio(AUDIO_CATEGORY, materialId) != null;
        if (!forceRefresh && hasFile) {
            updateAudioMetadata(material, AUDIO_CATEGORY + "/" + materialId + ".mp3", AiTaskConstants.STATUS_COMPLETED, userId);
            RuminationMaterialAudioStatusResponse ready = new RuminationMaterialAudioStatusResponse();
            ready.setMaterialId(String.valueOf(materialId));
            ready.setHasAudio(true);
            ready.setAudioStatus(AiTaskConstants.STATUS_COMPLETED);
            ready.setTaskStatus(AiTaskConstants.STATUS_COMPLETED);
            return ready;
        }

        if (!sceneArticleAudioService.isNlsConfigured()) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AI_PROVIDER_API_KEY_MISSING,
                    "未配置阿里云语音服务密钥（NLS_APP_KEY / ALIYUN_AK_ID / ALIYUN_AK_SECRET），可直接点击右侧 ▶ 使用实时朗读");
        }

        String idempotencyKey = buildIdempotencyKey(materialId);
        AiAsyncTask active = aiAsyncTaskService.findActiveByKey(
                userId, AiTaskConstants.TYPE_RUMINATION_MATERIAL_AUDIO, material.getWordbookId(), idempotencyKey);
        if (active != null) {
            RuminationMaterialAudioStatusResponse res = new RuminationMaterialAudioStatusResponse();
            res.setMaterialId(String.valueOf(materialId));
            res.setHasAudio(hasFile);
            res.setAudioStatus(active.getStatus());
            res.setTaskStatus(active.getStatus());
            res.setTaskId(String.valueOf(active.getId()));
            res.setErrorMessage(active.getErrorMessage());
            return res;
        }

        updateAudioMetadata(material, material.getAudioPath(), AiTaskConstants.STATUS_PENDING, userId);

        AiAsyncTask task = aiAsyncTaskService.create(
                userId,
                AiTaskConstants.TYPE_RUMINATION_MATERIAL_AUDIO,
                "反刍材料语音生成 · " + (StringUtils.hasText(material.getTitle()) ? material.getTitle() : ("材料#" + materialId)),
                material.getWordbookId(),
                materialId,
                null,
                AiTaskConstants.EXECUTION_IMMEDIATE,
                null,
                null,
                1,
                idempotencyKey,
                Map.of("materialId", String.valueOf(materialId), "forceRefresh", forceRefresh));

        RuminationMaterialAudioStatusResponse submitted = new RuminationMaterialAudioStatusResponse();
        submitted.setMaterialId(String.valueOf(materialId));
        submitted.setHasAudio(hasFile);
        submitted.setAudioStatus(task.getStatus());
        submitted.setTaskStatus(task.getStatus());
        submitted.setTaskId(String.valueOf(task.getId()));
        return submitted;
    }

    /**
     * 按需同步合成或返回缓存的知识反刍场景材料全文 MP3 音频流。
     */
    public Resource generateOrGetMaterialAudio(Long userId, Long materialId, boolean forceRefresh) {
        LearningRuminationMaterial material = requireOwnedMaterial(userId, materialId);
        if (!StringUtils.hasText(material.getArticleContent())) {
            throw LearningAssistantException.badRequest(LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "场景材料英文正文为空");
        }
        try {
            Resource resource = sceneArticleAudioService.synthesizeAndCacheAudio(
                    AUDIO_CATEGORY, materialId, material.getTitle(), material.getArticleContent(), forceRefresh);
            updateAudioMetadata(material, AUDIO_CATEGORY + "/" + materialId + ".mp3", AiTaskConstants.STATUS_COMPLETED, userId);
            return resource;
        } catch (RuntimeException ex) {
            updateAudioMetadata(material, material.getAudioPath(), AiTaskConstants.STATUS_FAILED, userId);
            throw ex;
        }
    }

    private LearningRuminationMaterial requireOwnedMaterial(Long userId, Long materialId) {
        if (materialId == null) {
            throw LearningAssistantException.badRequest(LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "场景材料 ID 不能为空");
        }
        LearningRuminationMaterial material = materialMapper.selectOne(
                new LambdaQueryWrapper<LearningRuminationMaterial>()
                        .eq(LearningRuminationMaterial::getId, materialId)
                        .eq(LearningRuminationMaterial::getUserId, userId)
                        .eq(LearningRuminationMaterial::getDeleted, 0)
                        .last("LIMIT 1"));
        if (material == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "场景材料不存在");
        }
        return material;
    }

    private void updateAudioMetadata(LearningRuminationMaterial material, String audioPath, String audioStatus, Long userId) {
        materialMapper.update(null, new UpdateWrapper<LearningRuminationMaterial>()
                .eq("id", material.getId())
                .set("audio_path", audioPath)
                .set("audio_status", audioStatus)
                .set("update_by", userId));
    }

    private static String buildIdempotencyKey(Long materialId) {
        return "rumination_audio:" + materialId;
    }
}
