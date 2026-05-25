package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dataobject.MotivationChild;
import com.chandler.motivation.domain.dto.child.ChildSaveRequest;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.MotivationChildService;
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
@RequestMapping("/api/v1/children")
public class ChildController {

    private final MotivationChildService childService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<MotivationChild>> list() {
        return ApiResponse.ok(childService.listByUser(authService.requireUser().getId()));
    }

    @PostMapping
    public ApiResponse<MotivationChild> create(@Valid @RequestBody ChildSaveRequest request) {
        return ApiResponse.ok(childService.create(request, authService.requireUser().getId()));
    }

    @PutMapping("/{childId}")
    public ApiResponse<MotivationChild> update(@PathVariable Long childId, @Valid @RequestBody ChildSaveRequest request) {
        return ApiResponse.ok(childService.update(childId, request, authService.requireUser().getId()));
    }

    @DeleteMapping("/{childId}")
    public ApiResponse<Boolean> delete(@PathVariable Long childId) {
        childService.delete(childId, authService.requireUser().getId());
        return ApiResponse.ok(Boolean.TRUE);
    }
}
