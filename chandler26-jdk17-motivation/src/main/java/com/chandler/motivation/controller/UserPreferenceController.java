package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.MotivationUserPreferenceService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/preferences")
public class UserPreferenceController {

    private final MotivationUserPreferenceService preferenceService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<Map<String, String>> list() {
        return ApiResponse.ok(preferenceService.listByUser(authService.requireUser().getId()));
    }

    @PutMapping
    public ApiResponse<Map<String, String>> save(@RequestBody Map<String, Object> preferences) {
        return ApiResponse.ok(preferenceService.savePreferences(authService.requireUser().getId(), preferences));
    }
}
