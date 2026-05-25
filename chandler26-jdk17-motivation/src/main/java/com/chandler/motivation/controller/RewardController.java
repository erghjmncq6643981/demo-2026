package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dataobject.MotivationReward;
import com.chandler.motivation.domain.dataobject.MotivationRewardExchange;
import com.chandler.motivation.domain.dto.reward.RewardExchangeConfirmRequest;
import com.chandler.motivation.domain.dto.reward.RewardExchangeRequest;
import com.chandler.motivation.domain.dto.reward.RewardExchangeReviewRequest;
import com.chandler.motivation.domain.dto.reward.RewardFulfillmentRequest;
import com.chandler.motivation.domain.dto.reward.RewardSaveRequest;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.MotivationRewardExchangeService;
import com.chandler.motivation.service.MotivationRewardService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final MotivationRewardService rewardService;
    private final MotivationRewardExchangeService rewardExchangeService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<MotivationReward>> list(@RequestParam Long childId) {
        return ApiResponse.ok(rewardService.listByChild(childId, authService.requireUser().getId()));
    }

    @PostMapping
    public ApiResponse<MotivationReward> create(@Valid @RequestBody RewardSaveRequest request) {
        return ApiResponse.ok(rewardService.create(request, authService.requireUser().getId()));
    }

    @PutMapping("/{rewardId}")
    public ApiResponse<MotivationReward> update(@PathVariable Long rewardId, @Valid @RequestBody RewardSaveRequest request) {
        return ApiResponse.ok(rewardService.update(rewardId, request, authService.requireUser().getId()));
    }

    @DeleteMapping("/{rewardId}")
    public ApiResponse<Boolean> delete(@PathVariable Long rewardId) {
        rewardService.delete(rewardId, authService.requireUser().getId());
        return ApiResponse.ok(Boolean.TRUE);
    }

    @PostMapping("/exchange")
    public ApiResponse<MotivationRewardExchange> exchange(@Valid @RequestBody RewardExchangeRequest request) {
        return ApiResponse.ok(rewardExchangeService.createRequest(authService.requireUser().getId(), request));
    }

    @GetMapping("/exchanges")
    public ApiResponse<List<MotivationRewardExchange>> exchanges(@RequestParam Long childId,
                                                                 @RequestParam(required = false) String status,
                                                                 @RequestParam(defaultValue = "50") Integer limit) {
        return ApiResponse.ok(rewardExchangeService.listByChild(childId, authService.requireUser().getId(), status, limit));
    }

    @PostMapping("/exchanges/{exchangeId}/approve")
    public ApiResponse<MotivationRewardExchange> approve(@PathVariable Long exchangeId,
                                                         @RequestBody(required = false) RewardExchangeReviewRequest request) {
        return ApiResponse.ok(rewardExchangeService.approve(exchangeId, request, authService.requireUser().getId()));
    }

    @PostMapping("/exchanges/{exchangeId}/reject")
    public ApiResponse<MotivationRewardExchange> reject(@PathVariable Long exchangeId,
                                                        @RequestBody(required = false) RewardExchangeReviewRequest request) {
        return ApiResponse.ok(rewardExchangeService.reject(exchangeId, request, authService.requireUser().getId()));
    }

    @PutMapping("/exchanges/{exchangeId}/fulfillment")
    public ApiResponse<MotivationRewardExchange> updateFulfillment(@PathVariable Long exchangeId,
                                                                   @RequestBody(required = false) RewardFulfillmentRequest request) {
        return ApiResponse.ok(rewardExchangeService.updateFulfillment(exchangeId, request, authService.requireUser().getId()));
    }

    @PostMapping("/exchanges/{exchangeId}/confirm")
    public ApiResponse<MotivationRewardExchange> confirm(@PathVariable Long exchangeId,
                                                         @RequestBody(required = false) RewardExchangeConfirmRequest request) {
        return ApiResponse.ok(rewardExchangeService.confirm(exchangeId, request, authService.requireUser().getId()));
    }
}
