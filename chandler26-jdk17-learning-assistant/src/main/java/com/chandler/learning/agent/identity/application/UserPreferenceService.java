package com.chandler.learning.agent.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.ai.agent.application.AiAgentService;
import com.chandler.learning.agent.ai.prompt.application.AiPromptTemplateService;
import com.chandler.learning.agent.identity.api.request.SpeechPreferenceRequest;
import com.chandler.learning.agent.identity.api.response.SpeechPreferenceResponse;
import com.chandler.learning.agent.identity.api.request.LearningSettingsRequest;
import com.chandler.learning.agent.identity.api.response.LearningSettingsResponse;
import com.chandler.learning.agent.identity.domain.entity.LearningUserPreference;
import com.chandler.learning.agent.identity.domain.enums.SpeechVoiceType;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.identity.infrastructure.mapper.LearningUserPreferenceMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.identity.domain.constant.UserPreferenceConstants;
import com.chandler.learning.agent.system.application.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 用户偏好配置服务。
 * <p>
 * 保存默认 Agent、提示词模板和发音等用户级学习偏好。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final LearningUserPreferenceMapper preferenceMapper;
    private final AiAgentService agentService;
    private final AiPromptTemplateService promptTemplateService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /**
     * 查询用户默认学习 Agent 和提示词模板。
     */
    public LearningSettingsResponse getLearningSettings(Long userId) {
        Map<String, String> preferences = loadPreferences(userId);
        LearningSettingsResponse response = new LearningSettingsResponse();
        response.setAgentCode(valueOrDefault(preferences,
                UserPreferenceConstants.KEY_LEARNING_AGENT_CODE, ""));
        response.setTemplateCode(valueOrDefault(preferences,
                UserPreferenceConstants.KEY_LEARNING_TEMPLATE_CODE, ""));
        return response;
    }

    /**
     * 保存用户默认学习 Agent 和提示词模板。
     */
    @Transactional(rollbackFor = Exception.class)
    public LearningSettingsResponse saveLearningSettings(Long userId, LearningSettingsRequest request) {
        String agentCode = request.getAgentCode().trim();
        String templateCode = request.getTemplateCode().trim();
        requireEnabledAgent(agentCode);
        requireEnabledTemplate(templateCode);
        upsertBatch(userId, Map.of(
                UserPreferenceConstants.KEY_LEARNING_AGENT_CODE, agentCode,
                UserPreferenceConstants.KEY_LEARNING_TEMPLATE_CODE, templateCode));

        systemLogService.record(userId, SystemLogType.PREFERENCE, "更新学习设置",
                "默认 Agent " + agentCode + "，默认模板 " + templateCode);
        log.info("用户「{}」更新学习设置：默认Agent={}，默认模板={}",
                userDisplayNameService.userName(userId), agentCode, templateCode);

        LearningSettingsResponse response = new LearningSettingsResponse();
        response.setAgentCode(agentCode);
        response.setTemplateCode(templateCode);
        return response;
    }

    /** 查询当前用户的朗读偏好。 */
    public SpeechPreferenceResponse getSpeechPreferences(Long userId) {
        return toSpeechResponse(loadPreferences(userId));
    }

    /** 保存当前用户的朗读偏好。 */
    @Transactional(rollbackFor = Exception.class)
    public SpeechPreferenceResponse saveSpeechPreferences(Long userId, SpeechPreferenceRequest request) {
        Map<String, String> preferences = loadPreferences(userId);
        SpeechPreferenceRequest resolvedRequest = request == null ? new SpeechPreferenceRequest() : request;

        String voiceType = resolvedRequest.getVoiceType() == null
                ? valueOrDefault(preferences, UserPreferenceConstants.KEY_SPEECH_VOICE_TYPE,
                SpeechVoiceType.US.getCode())
                : normalizeVoiceType(resolvedRequest.getVoiceType());
        String sentenceVoiceName = resolvedRequest.getSentenceVoiceName() == null
                ? valueOrDefault(preferences, UserPreferenceConstants.KEY_SPEECH_SENTENCE_VOICE_NAME, "")
                : trimToEmpty(resolvedRequest.getSentenceVoiceName());
        double sentenceRate = resolvedRequest.getSentenceRate() == null
                ? numberOrDefault(preferences, UserPreferenceConstants.KEY_SPEECH_SENTENCE_RATE,
                UserPreferenceConstants.SENTENCE_RATE_DEFAULT)
                : clamp(resolvedRequest.getSentenceRate(),
                UserPreferenceConstants.SENTENCE_RATE_MIN,
                UserPreferenceConstants.SENTENCE_RATE_MAX);
        double sentencePitch = resolvedRequest.getSentencePitch() == null
                ? numberOrDefault(preferences, UserPreferenceConstants.KEY_SPEECH_SENTENCE_PITCH,
                UserPreferenceConstants.SENTENCE_PITCH_DEFAULT)
                : clamp(resolvedRequest.getSentencePitch(),
                UserPreferenceConstants.SENTENCE_PITCH_MIN,
                UserPreferenceConstants.SENTENCE_PITCH_MAX);

        upsertBatch(userId, Map.of(
                UserPreferenceConstants.KEY_SPEECH_VOICE_TYPE, voiceType,
                UserPreferenceConstants.KEY_SPEECH_SENTENCE_VOICE_NAME, sentenceVoiceName,
                UserPreferenceConstants.KEY_SPEECH_SENTENCE_RATE, formatNumber(sentenceRate),
                UserPreferenceConstants.KEY_SPEECH_SENTENCE_PITCH, formatNumber(sentencePitch)));

        systemLogService.record(userId, SystemLogType.PREFERENCE, "更新发音设置",
                "默认发音 " + voiceType + "，句子语速 " + formatNumber(sentenceRate)
                        + "，句子音调 " + formatNumber(sentencePitch));
        log.info("用户「{}」更新了句子朗读设置：默认发音={}，句子音色={}，语速={}，音调={}",
                userDisplayNameService.userName(userId),
                voiceType,
                StringUtils.hasText(sentenceVoiceName) ? sentenceVoiceName : "自动",
                sentenceRate,
                sentencePitch);

        SpeechPreferenceResponse response = new SpeechPreferenceResponse();
        response.setVoiceType(voiceType);
        response.setSentenceVoiceName(sentenceVoiceName);
        response.setSentenceRate(sentenceRate);
        response.setSentencePitch(sentencePitch);
        return response;
    }

    private SpeechPreferenceResponse toSpeechResponse(Map<String, String> preferences) {
        SpeechPreferenceResponse response = new SpeechPreferenceResponse();
        response.setVoiceType(normalizeVoiceType(valueOrDefault(preferences,
                UserPreferenceConstants.KEY_SPEECH_VOICE_TYPE,
                SpeechVoiceType.US.getCode())));
        response.setSentenceVoiceName(valueOrDefault(preferences,
                UserPreferenceConstants.KEY_SPEECH_SENTENCE_VOICE_NAME, ""));
        response.setSentenceRate(clamp(numberOrDefault(preferences,
                        UserPreferenceConstants.KEY_SPEECH_SENTENCE_RATE,
                        UserPreferenceConstants.SENTENCE_RATE_DEFAULT),
                UserPreferenceConstants.SENTENCE_RATE_MIN,
                UserPreferenceConstants.SENTENCE_RATE_MAX));
        response.setSentencePitch(clamp(numberOrDefault(preferences,
                        UserPreferenceConstants.KEY_SPEECH_SENTENCE_PITCH,
                        UserPreferenceConstants.SENTENCE_PITCH_DEFAULT),
                UserPreferenceConstants.SENTENCE_PITCH_MIN,
                UserPreferenceConstants.SENTENCE_PITCH_MAX));
        return response;
    }

    private Map<String, String> loadPreferences(Long userId) {
        List<LearningUserPreference> preferences = preferenceMapper.selectList(new LambdaQueryWrapper<LearningUserPreference>()
                .eq(LearningUserPreference::getUserId, userId));
        Map<String, String> result = new LinkedHashMap<>();
        for (LearningUserPreference preference : preferences) {
            result.put(preference.getPreferenceKey(), preference.getPreferenceValue());
        }
        return result;
    }

    /**
     * 批量插入或更新用户偏好。
     */
    private void upsertBatch(Long userId, Map<String, String> keyValues) {
        if (keyValues == null || keyValues.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<LearningUserPreference> preferences = new java.util.ArrayList<>(keyValues.size());
        for (Map.Entry<String, String> entry : keyValues.entrySet()) {
            LearningUserPreference preference = new LearningUserPreference();
            preference.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            preference.setCreateBy(userId);
            preference.setUpdateBy(userId);
            preference.setUserId(userId);
            preference.setPreferenceKey(entry.getKey());
            preference.setPreferenceValue(entry.getValue());
            preference.setCreateTime(now);
            preference.setUpdateTime(now);
            preference.setDeleted(false);
            preference.setVersion(CommonConstants.ZERO);
            preferences.add(preference);
        }
        preferenceMapper.upsertBatch(preferences);
    }

    /**
     * 校验学习 Agent 存在且启用。
     */
    private void requireEnabledAgent(String agentCode) {
        agentService.requireEnabled(agentCode);
    }

    /**
     * 校验学习模板存在且启用。
     */
    private void requireEnabledTemplate(String templateCode) {
        promptTemplateService.requireEnabled(templateCode);
    }

    private String normalizeVoiceType(String value) {
        return SpeechVoiceType.of(value).getCode();
    }

    private String valueOrDefault(Map<String, String> preferences, String key, String fallback) {
        String value = preferences.get(key);
        return value == null ? fallback : value;
    }

    private double numberOrDefault(Map<String, String> preferences, String key, double fallback) {
        try {
            return Double.parseDouble(valueOrDefault(preferences, key, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
