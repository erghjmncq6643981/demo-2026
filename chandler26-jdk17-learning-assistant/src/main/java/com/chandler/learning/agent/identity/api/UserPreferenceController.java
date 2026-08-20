package com.chandler.learning.agent.identity.api;

import com.chandler.learning.agent.identity.api.SpeechPreferenceRequest;
import com.chandler.learning.agent.identity.api.SpeechPreferenceResponse;
import com.chandler.learning.agent.identity.api.LearningSettingsRequest;
import com.chandler.learning.agent.identity.api.LearningSettingsResponse;
import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.identity.application.AuthService;
import com.chandler.learning.agent.identity.application.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UserPreferenceController 类。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/preferences")
@Tag(name = "学习用户偏好")
public class UserPreferenceController {

    private final AuthService authService;
    private final UserPreferenceService userPreferenceService;

    /**
     * 获取个人学习设置。
     */
    @GetMapping("/learning-settings")
    @Operation(summary = "获取学习设置")
    public LearningSettingsResponse getLearningSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        LearningUser user = authService.requireUser(authorization);
        return userPreferenceService.getLearningSettings(user.getId());
    }

    /**
     * 保存个人学习设置。
     */
    @PutMapping("/learning-settings")
    @Operation(summary = "保存学习设置")
    public LearningSettingsResponse saveLearningSettings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody LearningSettingsRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return userPreferenceService.saveLearningSettings(user.getId(), request);
    }

    /**
     * 查询 {@code getSpeechPreferences} 相关业务。
     */
    @GetMapping("/speech")
    @Operation(summary = "获取发音偏好")
    public SpeechPreferenceResponse getSpeechPreferences(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        LearningUser user = authService.requireUser(authorization);
        return userPreferenceService.getSpeechPreferences(user.getId());
    }

    /**
     * 创建或保存 {@code saveSpeechPreferences} 相关业务。
     */
    @PutMapping("/speech")
    @Operation(summary = "保存发音偏好")
    public SpeechPreferenceResponse saveSpeechPreferences(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SpeechPreferenceRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return userPreferenceService.saveSpeechPreferences(user.getId(), request);
    }
}
