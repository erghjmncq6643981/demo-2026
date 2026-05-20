package com.chandler.learning.agent.controller.learning;

import com.chandler.learning.agent.domain.dto.learning.SpeechPreferenceRequest;
import com.chandler.learning.agent.domain.dto.learning.SpeechPreferenceResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import com.chandler.learning.agent.service.learning.AuthService;
import com.chandler.learning.agent.service.learning.UserPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/preferences")
@Tag(name = "学习用户偏好")
public class UserPreferenceController {

    private final AuthService authService;
    private final UserPreferenceService userPreferenceService;

    @GetMapping("/speech")
    @Operation(summary = "获取发音偏好")
    public SpeechPreferenceResponse getSpeechPreferences(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        LearningUser user = authService.requireUser(authorization);
        return userPreferenceService.getSpeechPreferences(user.getId());
    }

    @PutMapping("/speech")
    @Operation(summary = "保存发音偏好")
    public SpeechPreferenceResponse saveSpeechPreferences(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody SpeechPreferenceRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return userPreferenceService.saveSpeechPreferences(user.getId(), request);
    }
}
