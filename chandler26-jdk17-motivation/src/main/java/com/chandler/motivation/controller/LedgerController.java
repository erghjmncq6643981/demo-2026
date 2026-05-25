package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dataobject.MotivationPointLedger;
import com.chandler.motivation.domain.dto.points.PointAdjustRequest;
import com.chandler.motivation.domain.dto.points.PointSummaryResponse;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.MotivationChildPointBalanceService;
import com.chandler.motivation.service.MotivationChildService;
import com.chandler.motivation.service.MotivationPointLedgerService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/children/{childId}/points")
public class LedgerController {

    private final MotivationPointLedgerService pointLedgerService;
    private final MotivationChildPointBalanceService balanceService;
    private final MotivationChildService childService;
    private final AuthService authService;

    @GetMapping("/summary")
    public ApiResponse<PointSummaryResponse> summary(@PathVariable Long childId) {
        Long userId = authService.requireUser().getId();
        childService.requireViewAccess(childId, userId);
        PointSummaryResponse response = new PointSummaryResponse();
        response.setChildId(childId);
        response.setBalances(balanceService.listSummary(childId));
        return ApiResponse.ok(response);
    }

    @GetMapping("/ledger")
    public ApiResponse<List<MotivationPointLedger>> list(@PathVariable Long childId,
                                                         @RequestParam(required = false) String pointType,
                                                         @RequestParam(defaultValue = "50") Integer limit) {
        Long userId = authService.requireUser().getId();
        childService.requireViewAccess(childId, userId);
        return ApiResponse.ok(pointLedgerService.listByChild(childId, pointType, limit));
    }

    @PostMapping("/manual-adjust")
    public ApiResponse<MotivationPointLedger> manualAdjust(@PathVariable Long childId,
                                                           @RequestBody PointAdjustRequest request) {
        Long userId = authService.requireUser().getId();
        childService.requireManageAccess(childId, userId);
        return ApiResponse.ok(pointLedgerService.manualAdjust(childId, userId, request));
    }
}
