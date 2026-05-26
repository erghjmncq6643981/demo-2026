package com.chandler.motivation.controller;

import com.chandler.motivation.common.result.ApiResponse;
import com.chandler.motivation.domain.dataobject.MotivationChild;
import com.chandler.motivation.domain.dto.child.ChildSaveRequest;
import com.chandler.motivation.domain.dto.common.AvatarUploadResponse;
import com.chandler.motivation.service.AuthService;
import com.chandler.motivation.service.MotivationChildService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/{childId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AvatarUploadResponse> updateAvatar(@PathVariable Long childId,
                                                          @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(childService.updateAvatar(childId, authService.requireUser().getId(), file));
    }

    @GetMapping("/{childId}/avatar")
    public ResponseEntity<byte[]> readAvatar(@PathVariable Long childId) {
        var avatar = childService.readAvatar(childId, authService.requireUser().getId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.data());
    }
}
