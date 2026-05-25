package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dataobject.MotivationGoal;
import com.chandler.motivation.domain.dto.goal.GoalSaveRequest;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.MotivationGoalService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final MotivationGoalService goalService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<MotivationGoal>> list(@RequestParam Long childId) {
        return ApiResponse.ok(goalService.listByChild(childId, authService.requireUser().getId()));
    }

    @PostMapping
    public ApiResponse<MotivationGoal> create(@Valid @RequestBody GoalSaveRequest request) {
        return ApiResponse.ok(goalService.create(request, authService.requireUser().getId()));
    }

    @PutMapping("/{goalId}")
    public ApiResponse<MotivationGoal> update(@PathVariable Long goalId, @Valid @RequestBody GoalSaveRequest request) {
        return ApiResponse.ok(goalService.update(goalId, request, authService.requireUser().getId()));
    }

    @DeleteMapping("/{goalId}")
    public ApiResponse<Boolean> delete(@PathVariable Long goalId) {
        goalService.delete(goalId, authService.requireUser().getId());
        return ApiResponse.ok(Boolean.TRUE);
    }
}
