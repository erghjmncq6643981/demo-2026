package com.chandler.learning.agent.learning.application;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.chandler.learning.agent.config.speech.AliyunNlsProperties;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.learning.domain.entity.LearningPlanUnit;
import com.chandler.learning.agent.learning.domain.entity.LearningSceneMaterial;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper;
import com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneMaterialMapper;
import com.chandler.learning.agent.learning.api.response.SceneUnitAudioStatusResponse;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.task.domain.constant.AiTaskConstants;
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景文章阿里云 TTS 语音分段合成、拼接与持久化服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SceneArticleAudioService {

    private final LearningPlanUnitMapper unitMapper;
    private final LearningSceneMaterialMapper materialMapper;
    private final AliyunNlsProperties nlsProperties;
    private final AiAsyncTaskService aiAsyncTaskService;

    @Value("${learning.audio.storage-path:./data/audio/}")
    private String storagePath;

    private final ConcurrentHashMap<Long, Object> unitLocks = new ConcurrentHashMap<>();

    /**
     * 文本清洗：去除 Markdown 语法、特殊符号与标签，提取纯净朗读文本。
     */
    public String cleanTtsText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        return rawText
                // 去除代码块
                .replaceAll("```[\\s\\S]*?```", "")
                // 去除行内代码与反引号
                .replaceAll("`([^`]+)`", "$1")
                // 去除 Markdown 标题 (# )
                .replaceAll("(?m)^#{1,6}\\s+", "")
                // 去除加粗与斜体 (**word**, *word*, __word__, _word_)
                .replaceAll("\\*{1,2}(.*?)\\*{1,2}", "$1")
                .replaceAll("_{1,2}(.*?)_{1,2}", "$1")
                // 去除 HTML 标签与实体
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&[a-zA-Z]+;", "")
                // 去除无序/有序列表前缀 (- , * , 1. )
                .replaceAll("(?m)^[\\s*\\-+]+\\s+", "")
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                // 规整多余空白与换行
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }

    /**
     * 智能分句切片算法：在句末标点处断句，每一句作为一个独立切片。
     * 当按句独立合成时，各句的停顿和语调更自然，拼接后产生清晰、自然的句间停顿，更利于学习者精读跟读。
     */
    public List<String> splitIntoChunks(String text, int maxChunkLength) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }
        int limit = maxChunkLength > 0 ? maxChunkLength : 200;

        // 按段落与句子边界精确分句
        String[] sentences = text.split("(?<=[.!?])\\s+|\\n+");

        for (String sentence : sentences) {
            String trimmedSentence = sentence.trim();
            if (trimmedSentence.isEmpty()) {
                continue;
            }

            // 若单句长度在限制以内，单独作为切片，保持完整的句子韵律和句尾停顿
            if (trimmedSentence.length() <= limit) {
                chunks.add(trimmedSentence);
            } else {
                // 超长句按从句逗号/分号进一步切分
                String[] subClauses = trimmedSentence.split("(?<=[,;:])\\s+");
                StringBuilder currentChunk = new StringBuilder();
                for (String clause : subClauses) {
                    String sub = clause.trim();
                    if (sub.isEmpty()) continue;
                    if (currentChunk.length() + sub.length() + 1 <= limit) {
                        if (currentChunk.length() > 0) currentChunk.append(" ");
                        currentChunk.append(sub);
                    } else {
                        if (currentChunk.length() > 0) {
                            chunks.add(currentChunk.toString().trim());
                            currentChunk.setLength(0);
                        }
                        while (sub.length() > limit) {
                            chunks.add(sub.substring(0, limit).trim());
                            sub = sub.substring(limit).trim();
                        }
                        if (!sub.isEmpty()) {
                            currentChunk.append(sub);
                        }
                    }
                }
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }
            }
        }

        return chunks;
    }

    /**
     * 查询本地已存在的场景音频文件。
     */
    public Resource getExistingSceneAudio(Long unitId) {
        if (unitId == null) {
            return null;
        }
        Path targetFile = Paths.get(storagePath, "scene", unitId + ".mp3");
        if (Files.exists(targetFile)) {
            try {
                if (Files.size(targetFile) >= 100) {
                    return new FileSystemResource(targetFile);
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /**
     * 判断指定场景单元的音频文件是否已生成就绪。
     */
    public boolean hasAudio(Long unitId) {
        return getExistingSceneAudio(unitId) != null;
    }

    /**
     * 获取指定场景单元的音频就绪状态与异步生成任务状态。
     */
    public SceneUnitAudioStatusResponse getAudioStatus(Long userId, Long unitId) {
        boolean hasAudio = hasAudio(unitId);

        SceneUnitAudioStatusResponse response = new SceneUnitAudioStatusResponse();
        response.setUnitId(unitId);
        response.setHasAudio(hasAudio);

        String idempotencyKey = "scene_audio:" + unitId;
        AiAsyncTask activeTask = aiAsyncTaskService.findActiveByKey(userId,
                AiTaskConstants.TYPE_SCENE_ARTICLE_AUDIO, null, idempotencyKey);
        if (activeTask == null) {
            LearningPlanUnit unit = unitMapper.selectById(unitId);
            if (unit != null && unit.getPlanId() != null) {
                activeTask = aiAsyncTaskService.findActiveByKey(userId,
                        AiTaskConstants.TYPE_SCENE_ARTICLE_AUDIO, unit.getPlanId(), idempotencyKey);
            }
        }

        if (activeTask != null) {
            response.setTaskStatus(activeTask.getStatus());
            response.setTaskId(activeTask.getId());
            response.setErrorMessage(activeTask.getErrorMessage());
        } else if (hasAudio) {
            response.setTaskStatus(AiTaskConstants.STATUS_COMPLETED);
        } else {
            response.setTaskStatus("none");
        }
        return response;
    }

    /**
     * 异步提交场景文章语音生成任务。
     */
    public SceneUnitAudioStatusResponse submitAudioGenerationTask(Long userId, Long unitId, boolean forceRefresh) {
        LearningPlanUnit unit = unitMapper.selectById(unitId);
        if (unit == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND, "场景学习单元不存在");
        }

        boolean hasAudio = hasAudio(unitId);
        if (!forceRefresh && hasAudio) {
            SceneUnitAudioStatusResponse response = new SceneUnitAudioStatusResponse();
            response.setUnitId(unitId);
            response.setHasAudio(true);
            response.setTaskStatus(AiTaskConstants.STATUS_COMPLETED);
            return response;
        }

        String idempotencyKey = "scene_audio:" + unitId;
        AiAsyncTask active = aiAsyncTaskService.findActiveByKey(userId,
                AiTaskConstants.TYPE_SCENE_ARTICLE_AUDIO, unit.getPlanId(), idempotencyKey);
        if (active != null) {
            SceneUnitAudioStatusResponse response = new SceneUnitAudioStatusResponse();
            response.setUnitId(unitId);
            response.setHasAudio(hasAudio);
            response.setTaskStatus(active.getStatus());
            response.setTaskId(active.getId());
            response.setErrorMessage(active.getErrorMessage());
            return response;
        }

        Map<String, Object> payload = Map.of("forceRefresh", forceRefresh);
        AiAsyncTask task = aiAsyncTaskService.create(
                userId,
                AiTaskConstants.TYPE_SCENE_ARTICLE_AUDIO,
                "场景文章语音生成 · " + (unit.getTitle() != null ? unit.getTitle() : ("单元#" + unitId)),
                unit.getPlanId(),
                unitId,
                null,
                AiTaskConstants.EXECUTION_IMMEDIATE,
                null,
                null,
                1,
                idempotencyKey,
                payload);

        SceneUnitAudioStatusResponse response = new SceneUnitAudioStatusResponse();
        response.setUnitId(unitId);
        response.setHasAudio(hasAudio);
        response.setTaskStatus(task.getStatus());
        response.setTaskId(task.getId());
        return response;
    }

    /**
     * 查询所有已生成有效场景学习材料的单元 ID 列表（用于定时缺省巡检）。
     */
    public List<Long> collectAllSceneUnitIds() {
        QueryWrapper<LearningPlanUnit> qw = new QueryWrapper<>();
        qw.select("id");
        qw.isNotNull("scene_material_id");
        qw.eq("deleted", false);
        List<LearningPlanUnit> units = unitMapper.selectList(qw);
        if (units == null || units.isEmpty()) {
            return List.of();
        }
        return units.stream()
                .map(LearningPlanUnit::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    /**
     * 检查并确保指定场景单元的文章音频已落盘，若缺省则自动调用阿里云 TTS 生成。
     *
     * @param unitId 场景单元 ID
     * @return 本次新合成成功的音频文件数（0 或 1）
     */
    public int syncEnsureSceneAudio(Long unitId) {
        if (unitId == null) {
            return 0;
        }
        if (hasAudio(unitId)) {
            return 0;
        }
        try {
            Resource resource = generateOrGetSceneAudio(unitId, false);
            return resource != null && resource.exists() ? 1 : 0;
        } catch (Exception ex) {
            log.warn("缺省检查中合成场景文章语音异常 unitId={}: {}", unitId, ex.getMessage());
            return 0;
        }
    }

    private volatile String cachedToken;
    private volatile long tokenExpireTime = 0L;

    private synchronized String getValidAccessToken() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        if (cachedToken != null && now < (tokenExpireTime - 600)) {
            return cachedToken;
        }
        AccessToken accessToken = new AccessToken(nlsProperties.getAkId(), nlsProperties.getAkSecret());
        accessToken.apply();
        if (!StringUtils.hasText(accessToken.getToken())) {
            throw new IllegalStateException("获取阿里云 NLS AccessToken 返回空");
        }
        this.cachedToken = accessToken.getToken();
        this.tokenExpireTime = accessToken.getExpireTime() > 0 ? accessToken.getExpireTime() : (now + 86000);
        log.info("已获取并缓存阿里云 NLS AccessToken，有效期至: {}", tokenExpireTime);
        return cachedToken;
    }

    /**
     * 按需生成或读取场景文章的完整 MP3 语音文件。
     */
    public Resource generateOrGetSceneAudio(Long unitId) {
        return generateOrGetSceneAudio(unitId, false);
    }

    /**
     * 按需生成或读取场景文章的完整 MP3 语音文件（支持强制重新合成）。
     */
    public Resource generateOrGetSceneAudio(Long unitId, boolean forceRefresh) {
        if (unitId == null) {
            throw LearningAssistantException.badRequest(LearningErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND, "单元 ID 不能为空");
        }

        // 1. 若非强制刷新，检查本地磁盘是否已就绪
        if (!forceRefresh) {
            Resource existing = getExistingSceneAudio(unitId);
            if (existing != null) {
                return existing;
            }
        }

        // 2. 校验阿里云 NLS 配置
        if (!StringUtils.hasText(nlsProperties.getAppKey())
                || !StringUtils.hasText(nlsProperties.getAkId())
                || !StringUtils.hasText(nlsProperties.getAkSecret())) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AI_PROVIDER_API_KEY_MISSING,
                    "未配置阿里云语音服务密钥（NLS_APP_KEY / ALIYUN_AK_ID / ALIYUN_AK_SECRET），无法进行 AI 真人朗读合成");
        }

        // 3. 读取场景单元与关联的教材材料
        LearningPlanUnit unit = unitMapper.selectById(unitId);
        if (unit == null) {
            throw LearningAssistantException.notFound(LearningErrorCode.LEARNING_PLAN_UNIT_NOT_FOUND, "场景学习单元不存在");
        }
        if (unit.getSceneMaterialId() == null) {
            throw LearningAssistantException.badRequest(LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "该单元尚未生成场景学习材料");
        }
        LearningSceneMaterial material = materialMapper.selectById(unit.getSceneMaterialId());
        if (material == null || !StringUtils.hasText(material.getLearningText())) {
            throw LearningAssistantException.badRequest(LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "场景英文学习材料正文为空");
        }

        // 4. 并发锁控制，避免对同一单元重复调用阿里云 TTS
        Object lock = unitLocks.computeIfAbsent(unitId, k -> new Object());
        synchronized (lock) {
            try {
                if (!forceRefresh) {
                    Resource existing = getExistingSceneAudio(unitId);
                    if (existing != null) {
                        return existing;
                    }
                }

                Path sceneDir = Paths.get(storagePath, "scene");
                if (!Files.exists(sceneDir)) {
                    Files.createDirectories(sceneDir);
                }
                Path targetFile = sceneDir.resolve(unitId + ".mp3");
                Path tmpFile = sceneDir.resolve(unitId + ".mp3.tmp." + System.currentTimeMillis());

                String cleanText = cleanTtsText(material.getLearningText());
                List<String> chunks = splitIntoChunks(cleanText, nlsProperties.getMaxChunkLength());
                if (chunks.isEmpty()) {
                    throw LearningAssistantException.badRequest(LearningErrorCode.LEARNING_SCENE_MATERIAL_NOT_FOUND, "清洗后无有效朗读文本");
                }

                log.info("开始合成场景文章音频 unitId={} title={} voice={} 切片数={}",
                        unitId, unit.getTitle(), nlsProperties.getVoice(), chunks.size());

                String token;
                try {
                    token = getValidAccessToken();
                } catch (Exception ex) {
                    log.error("申请阿里云 NLS AccessToken 异常: {}", ex.getMessage());
                    throw LearningAssistantException.externalService(LearningErrorCode.EXTERNAL_SERVICE_CALL_FAILED, "获取阿里云 NLS AccessToken 失败: " + ex.getMessage(), ex);
                }

                NlsClient nlsClient = new NlsClient(nlsProperties.getGatewayUrl(), token);

                try (FileOutputStream fos = new FileOutputStream(tmpFile.toFile())) {
                    for (int i = 0; i < chunks.size(); i++) {
                        String chunk = chunks.get(i);
                        log.debug("正在合成第 {}/{} 段音频 ({} chars): {}", (i + 1), chunks.size(), chunk.length(), chunk);
                        byte[] audioBytes = synthesizeChunk(nlsClient, chunk, nlsProperties.getAppKey(), nlsProperties.getVoice(), nlsProperties.getSpeechRate());
                        if (audioBytes != null && audioBytes.length > 0) {
                            fos.write(audioBytes);
                        }
                    }
                    fos.flush();
                } finally {
                    try {
                        nlsClient.shutdown();
                    } catch (Exception ignored) {
                    }
                }

                if (Files.exists(tmpFile) && Files.size(tmpFile) >= 100) {
                    Files.move(tmpFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    log.info("场景文章音频合成成功已落盘: {} ({} bytes)", targetFile, Files.size(targetFile));
                    return new FileSystemResource(targetFile);
                } else {
                    Files.deleteIfExists(tmpFile);
                    throw LearningAssistantException.system(LearningErrorCode.SYSTEM_UNEXPECTED, "语音合成数据写入为空", null);
                }
            } catch (LearningAssistantException lae) {
                throw lae;
            } catch (Exception ex) {
                log.error("合成场景文章语音异常 unitId={}: {}", unitId, ex.getMessage(), ex);
                throw LearningAssistantException.externalService(LearningErrorCode.EXTERNAL_SERVICE_CALL_FAILED, "语音合成失败: " + ex.getMessage(), ex);
            } finally {
                unitLocks.remove(unitId);
            }
        }
    }

    private byte[] synthesizeChunk(NlsClient client, String text, String appKey, String voice, Integer speechRate) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        SpeechSynthesizer synthesizer = null;
        try {
            SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {
                /** 接收并写入语音流二进制数据。 */
                @Override
                public void onMessage(ByteBuffer message) {
                    byte[] bytes = new byte[message.remaining()];
                    message.get(bytes, 0, bytes.length);
                    baos.write(bytes, 0, bytes.length);
                }

                /** 语音合成完成通知。 */
                @Override
                public void onComplete(SpeechSynthesizerResponse response) {
                    log.debug("分段 TTS 完成: status={}", response.getStatus());
                }

                /** 语音合成失败通知。 */
                @Override
                public void onFail(SpeechSynthesizerResponse response) {
                    log.warn("分段 TTS 失败: status={} statusText={}", response.getStatus(), response.getStatusText());
                }
            };

            synthesizer = new SpeechSynthesizer(client, listener);
            synthesizer.setAppKey(appKey);
            synthesizer.setFormat(OutputFormatEnum.MP3);
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            synthesizer.setVoice(StringUtils.hasText(voice) ? voice : "siyue");
            synthesizer.setPitchRate(0);
            synthesizer.setSpeechRate(speechRate != null ? speechRate : -120);
            synthesizer.setText(text);

            synthesizer.start();
            synthesizer.waitForComplete();
            return baos.toByteArray();
        } finally {
            if (synthesizer != null) {
                synthesizer.close();
            }
        }
    }
}
