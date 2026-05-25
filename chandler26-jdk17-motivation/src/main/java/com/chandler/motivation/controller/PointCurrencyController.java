package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dataobject.MotivationPointCurrency;
import com.chandler.motivation.domain.dto.points.PointCurrencySaveRequest;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.MotivationPointCurrencyService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/children/{childId}/points/currencies")
public class PointCurrencyController {

    private final MotivationPointCurrencyService pointCurrencyService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<MotivationPointCurrency>> list(@PathVariable Long childId) {
        return ApiResponse.ok(pointCurrencyService.listByChild(childId, authService.requireUser().getId()));
    }

    @PostMapping
    public ApiResponse<MotivationPointCurrency> create(@PathVariable Long childId,
                                                       @Valid @RequestBody PointCurrencySaveRequest request) {
        request.setChildId(childId);
        return ApiResponse.ok(pointCurrencyService.create(request, authService.requireUser().getId()));
    }

    @PutMapping("/{currencyId}")
    public ApiResponse<MotivationPointCurrency> update(@PathVariable Long childId,
                                                       @PathVariable Long currencyId,
                                                       @Valid @RequestBody PointCurrencySaveRequest request) {
        request.setChildId(childId);
        return ApiResponse.ok(pointCurrencyService.update(currencyId, request, authService.requireUser().getId()));
    }

    @DeleteMapping("/{currencyId}")
    public ApiResponse<Boolean> delete(@PathVariable Long currencyId) {
        pointCurrencyService.delete(currencyId, authService.requireUser().getId());
        return ApiResponse.ok(Boolean.TRUE);
    }
}
