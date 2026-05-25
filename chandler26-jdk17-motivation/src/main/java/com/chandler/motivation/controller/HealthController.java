package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({"/api/health", "/api/v1/health"})
    public ApiResponse<String> health() {
        return ApiResponse.ok("ok");
    }
}
