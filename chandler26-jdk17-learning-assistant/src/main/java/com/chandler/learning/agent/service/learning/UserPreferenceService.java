package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.learning.SpeechPreferenceRequest;
import com.chandler.learning.agent.domain.dto.learning.SpeechPreferenceResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningUserPreference;
import com.chandler.learning.agent.mapper.learning.LearningUserPreferenceMapper;
import com.chandler.learning.agent.support.LearningConstants;
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
 * 当前主要保存发音偏好，后续可继续扩展界面、复习节奏等用户级配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final LearningUserPreferenceMapper preferenceMapper;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    public SpeechPreferenceResponse getSpeechPreferences(Long userId) {
        return toSpeechResponse(loadPreferences(userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public SpeechPreferenceResponse saveSpeechPreferences(Long userId, SpeechPreferenceRequest request) {
        Map<String, String> preferences = loadPreferences(userId);
        SpeechPreferenceRequest resolvedRequest = request == null ? new SpeechPreferenceRequest() : request;

        String voiceType = resolvedRequest.getVoiceType() == null
                ? valueOrDefault(preferences, LearningConstants.UserPreference.KEY_SPEECH_VOICE_TYPE,
                LearningConstants.UserPreference.VOICE_TYPE_US)
                : normalizeVoiceType(resolvedRequest.getVoiceType());
        String sentenceVoiceName = resolvedRequest.getSentenceVoiceName() == null
                ? valueOrDefault(preferences, LearningConstants.UserPreference.KEY_SPEECH_SENTENCE_VOICE_NAME, "")
                : trimToEmpty(resolvedRequest.getSentenceVoiceName());
        double sentenceRate = resolvedRequest.getSentenceRate() == null
                ? numberOrDefault(preferences, LearningConstants.UserPreference.KEY_SPEECH_SENTENCE_RATE,
                LearningConstants.UserPreference.SENTENCE_RATE_DEFAULT)
                : clamp(resolvedRequest.getSentenceRate(),
                LearningConstants.UserPreference.SENTENCE_RATE_MIN,
                LearningConstants.UserPreference.SENTENCE_RATE_MAX);
        double sentencePitch = resolvedRequest.getSentencePitch() == null
                ? numberOrDefault(preferences, LearningConstants.UserPreference.KEY_SPEECH_SENTENCE_PITCH,
                LearningConstants.UserPreference.SENTENCE_PITCH_DEFAULT)
                : clamp(resolvedRequest.getSentencePitch(),
                LearningConstants.UserPreference.SENTENCE_PITCH_MIN,
                LearningConstants.UserPreference.SENTENCE_PITCH_MAX);

        upsert(userId, LearningConstants.UserPreference.KEY_SPEECH_VOICE_TYPE, voiceType);
        upsert(userId, LearningConstants.UserPreference.KEY_SPEECH_SENTENCE_VOICE_NAME, sentenceVoiceName);
        upsert(userId, LearningConstants.UserPreference.KEY_SPEECH_SENTENCE_RATE, formatNumber(sentenceRate));
        upsert(userId, LearningConstants.UserPreference.KEY_SPEECH_SENTENCE_PITCH, formatNumber(sentencePitch));

        systemLogService.record(userId, "preference", "更新发音设置",
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
                LearningConstants.UserPreference.KEY_SPEECH_VOICE_TYPE,
                LearningConstants.UserPreference.VOICE_TYPE_US)));
        response.setSentenceVoiceName(valueOrDefault(preferences,
                LearningConstants.UserPreference.KEY_SPEECH_SENTENCE_VOICE_NAME, ""));
        response.setSentenceRate(clamp(numberOrDefault(preferences,
                        LearningConstants.UserPreference.KEY_SPEECH_SENTENCE_RATE,
                        LearningConstants.UserPreference.SENTENCE_RATE_DEFAULT),
                LearningConstants.UserPreference.SENTENCE_RATE_MIN,
                LearningConstants.UserPreference.SENTENCE_RATE_MAX));
        response.setSentencePitch(clamp(numberOrDefault(preferences,
                        LearningConstants.UserPreference.KEY_SPEECH_SENTENCE_PITCH,
                        LearningConstants.UserPreference.SENTENCE_PITCH_DEFAULT),
                LearningConstants.UserPreference.SENTENCE_PITCH_MIN,
                LearningConstants.UserPreference.SENTENCE_PITCH_MAX));
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

    private void upsert(Long userId, String key, String value) {
        LearningUserPreference preference = preferenceMapper.selectOne(new LambdaQueryWrapper<LearningUserPreference>()
                .eq(LearningUserPreference::getUserId, userId)
                .eq(LearningUserPreference::getPreferenceKey, key)
                .last(LearningConstants.SQL_LIMIT_ONE));
        LocalDateTime now = LocalDateTime.now();
        if (preference == null) {
            preference = new LearningUserPreference();
            preference.setUserId(userId);
            preference.setPreferenceKey(key);
            preference.setPreferenceValue(value);
            preference.setCreateTime(now);
            preference.setUpdateTime(now);
            preferenceMapper.insert(preference);
            return;
        }
        preference.setPreferenceValue(value);
        preference.setUpdateTime(now);
        preferenceMapper.updateById(preference);
    }

    private String normalizeVoiceType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return LearningConstants.UserPreference.VOICE_TYPE_UK.equals(normalized)
                ? LearningConstants.UserPreference.VOICE_TYPE_UK
                : LearningConstants.UserPreference.VOICE_TYPE_US;
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
