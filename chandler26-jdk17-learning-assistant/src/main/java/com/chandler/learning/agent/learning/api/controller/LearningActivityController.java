package com.chandler.learning.agent.learning.api.controller;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.learning.api.response.LearningActivityResponse;
import com.chandler.learning.agent.learning.application.LearningActivityService;
import com.chandler.learning.agent.learning.domain.constant.LearningActivityConstants;
import com.chandler.learning.agent.security.CurrentUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/** 学习活动统计接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/activity")
@Tag(name = "学习活动")
public class LearningActivityController {

    private final CurrentUserContext currentUserContext;
    private final LearningActivityService activityService;

    /** 查询最近一段时间的学习活动日汇总。 */
    @GetMapping
    @Operation(summary = "查询学习活动热力图")
    public CompletableFuture<LearningActivityResponse> activity(
            @RequestParam(defaultValue = "365") Integer days) {
        LearningUser user = currentUserContext.requireUser();
        return activityService.activityAsync(
                user.getId(), days == null ? LearningActivityConstants.DEFAULT_DAYS : days);
    }
}
