package com.chandler.learning.agent.learning.api.controller;

import com.chandler.learning.agent.learning.api.request.SceneMaterialNoteRequest;
import com.chandler.learning.agent.learning.api.response.SceneMaterialNoteResponse;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.learning.application.LearningSceneMaterialNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 场景材料 Markdown 笔记接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/plans/{planId}/units/{unitId}/note")
@Tag(name = "场景材料笔记")
public class LearningSceneMaterialNoteController {

    private final CurrentUserContext currentUserContext;
    private final LearningSceneMaterialNoteService noteService;

    /** 查询场景材料笔记。 */
    @GetMapping
    @Operation(summary = "查询场景材料笔记")
    public SceneMaterialNoteResponse get(
            @PathVariable Long planId,
            @PathVariable Long unitId) {
        LearningUser user = currentUserContext.requireUser();
        return noteService.get(user.getId(), planId, unitId);
    }

    /** 保存场景材料 Markdown 笔记。 */
    @PutMapping
    @Operation(summary = "保存场景材料 Markdown 笔记")
    public SceneMaterialNoteResponse save(
            @PathVariable Long planId,
            @PathVariable Long unitId,
            @Valid @RequestBody SceneMaterialNoteRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return noteService.save(user.getId(), planId, unitId, request);
    }
}
