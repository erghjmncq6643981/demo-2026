package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dataobject.MotivationChild;
import com.chandler.motivation.domain.dto.log.ActivityLogPageResponse;
import com.chandler.motivation.domain.dto.log.ChildActivityLogResponse;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.MotivationChildService;
import com.chandler.motivation.service.MotivationSystemLogService;
import com.chandler.motivation.support.MotivationConstants;
import com.chandler.motivation.support.MotivationEnums;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/activity-logs")
public class ActivityLogController {

    private final AuthService authService;
    private final MotivationChildService childService;
    private final MotivationSystemLogService systemLogService;

    @GetMapping("/children")
    public ApiResponse<List<ChildActivityLogResponse>> childActivities(
            @RequestParam(required = false) Long childId,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = authService.requireUser().getId();
        List<Long> childIds = resolveVisibleChildIds(childId, userId);
        return ApiResponse.ok(systemLogService.listChildActivities(childIds,
                Math.min(limit, MotivationConstants.Pagination.ACTIVITY_LOG_MAX_LIMIT)));
    }

    @GetMapping("/children/page")
    public ApiResponse<ActivityLogPageResponse> childActivitiesPage(
            @RequestParam(required = false) Long childId,
            @RequestParam(defaultValue = "GROWTH") String category,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "5") int pageSize) {
        Long userId = authService.requireUser().getId();
        List<Long> childIds = resolveVisibleChildIds(childId, userId);
        MotivationEnums.ActivityLogCategory resolvedCategory = MotivationEnums.fromCode(
                MotivationEnums.ActivityLogCategory.class,
                category,
                MotivationEnums.ActivityLogCategory.GROWTH);
        return ApiResponse.ok(systemLogService.pageChildActivities(childIds, resolvedCategory, pageNo, pageSize));
    }

    private List<Long> resolveVisibleChildIds(Long childId, Long userId) {
        if (childId != null) {
            childService.requireViewAccess(childId, userId);
            return List.of(childId);
        }
        return childService.listByUser(userId).stream()
                .map(MotivationChild::getId)
                .toList();
    }
}
