package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.learning.application.SceneArticleAudioService;
import com.chandler.learning.agent.reading.api.response.RuminationMaterialAudioStatusResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationMaterial;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningRuminationMaterialMapper;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("知识反刍文章 AI 语音服务测试")
class RuminationAudioServiceTest {

    @Mock
    private LearningRuminationMaterialMapper materialMapper;
    @Mock
    private SceneArticleAudioService sceneArticleAudioService;
    @Mock
    private AiAsyncTaskService aiAsyncTaskService;

    private RuminationAudioService ruminationAudioService;

    @BeforeEach
    void setUp() {
        ruminationAudioService = new RuminationAudioService(
                materialMapper, sceneArticleAudioService, aiAsyncTaskService);
    }

    @Test
    @DisplayName("音频已存在时返回 completed 状态且异步提交直接复用")
    void submitAudioTask_returnsCompletedWhenAudioCached() {
        LearningRuminationMaterial material = buildMaterial(1001L, 8801L);
        when(materialMapper.selectOne(any())).thenReturn(material);
        Resource cached = new ByteArrayResource(new byte[256]);
        when(sceneArticleAudioService.getExistingStoredAudio("rumination", 8801L)).thenReturn(cached);

        RuminationMaterialAudioStatusResponse status = ruminationAudioService.getAudioStatus(1001L, 8801L);
        assertThat(status.getHasAudio()).isTrue();
        assertThat(status.getTaskStatus()).isEqualTo(AiTaskConstants.STATUS_COMPLETED);

        RuminationMaterialAudioStatusResponse submit = ruminationAudioService.submitAudioGenerationTask(1001L, 8801L, false);
        assertThat(submit.getHasAudio()).isTrue();
        assertThat(submit.getTaskStatus()).isEqualTo(AiTaskConstants.STATUS_COMPLETED);
    }

    @Test
    @DisplayName("未配置阿里云 NLS 密钥时异步提交立即给出明确提示")
    void submitAudioTask_throwsClearErrorWhenNlsNotConfigured() {
        LearningRuminationMaterial material = buildMaterial(1001L, 8801L);
        when(materialMapper.selectOne(any())).thenReturn(material);
        when(sceneArticleAudioService.getExistingStoredAudio("rumination", 8801L)).thenReturn(null);
        when(sceneArticleAudioService.isNlsConfigured()).thenReturn(false);

        assertThatThrownBy(() -> ruminationAudioService.submitAudioGenerationTask(1001L, 8801L, false))
                .isInstanceOf(LearningAssistantException.class)
                .extracting("errorCode")
                .isEqualTo(LearningErrorCode.AI_PROVIDER_API_KEY_MISSING.name());
    }

    @Test
    @DisplayName("已配置阿里云 NLS 时提交异步合成任务并在命中时幂等返回")
    void submitAudioTask_createsTaskWhenConfigured() {
        LearningRuminationMaterial material = buildMaterial(1001L, 8801L);
        when(materialMapper.selectOne(any())).thenReturn(material);
        when(sceneArticleAudioService.getExistingStoredAudio("rumination", 8801L)).thenReturn(null);
        when(sceneArticleAudioService.isNlsConfigured()).thenReturn(true);
        when(aiAsyncTaskService.findActiveByKey(eq(1001L), eq(AiTaskConstants.TYPE_RUMINATION_MATERIAL_AUDIO), eq(2001L), eq("rumination_audio:8801")))
                .thenReturn(null);

        AiAsyncTask created = new AiAsyncTask();
        created.setId(9901L);
        created.setStatus(AiTaskConstants.STATUS_PENDING);
        when(aiAsyncTaskService.create(
                eq(1001L),
                eq(AiTaskConstants.TYPE_RUMINATION_MATERIAL_AUDIO),
                anyString(),
                eq(2001L),
                eq(8801L),
                any(),
                eq(AiTaskConstants.EXECUTION_IMMEDIATE),
                any(),
                any(),
                anyInt(),
                eq("rumination_audio:8801"),
                any()
        )).thenReturn(created);

        RuminationMaterialAudioStatusResponse res = ruminationAudioService.submitAudioGenerationTask(1001L, 8801L, false);
        assertThat(res.getTaskId()).isEqualTo("9901");
        assertThat(res.getTaskStatus()).isEqualTo(AiTaskConstants.STATUS_PENDING);
        verify(materialMapper).update(eq(null), any());
    }

    private static LearningRuminationMaterial buildMaterial(Long userId, Long materialId) {
        LearningRuminationMaterial m = new LearningRuminationMaterial();
        m.setId(materialId);
        m.setUserId(userId);
        m.setWordbookId(2001L);
        m.setScenarioCode("produce_kitchen");
        m.setTitle("Fresh Market");
        m.setArticleContent("We peel carrots and boil potatoes for dinner.");
        m.setVersion(0);
        return m;
    }
}
