package com.chandler.learning.agent.vocabulary.api.controller;

import com.chandler.learning.agent.vocabulary.api.request.VocabularyCatalogAnalysisRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyCatalogAnalysisResponse;
import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.security.LearningPermission;
import com.chandler.learning.agent.security.RequirePermission;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公共词本关联分析接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vocabulary-catalogs")
@Tag(name = "公共词本关联分析")
public class VocabularyCatalogAnalysisController {

    private final CurrentUserContext currentUserContext;
    private final VocabularyCatalogAnalysisService analysisService;

    @GetMapping("/{catalogVersionId}/analysis")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "查询公共词本版本关联分析状态")
    public VocabularyCatalogAnalysisResponse detail(
            @PathVariable Long catalogVersionId) {
        LearningUser user = currentUserContext.requireUser();
        return analysisService.detail(user.getId(), catalogVersionId);
    }

    @PostMapping("/{catalogVersionId}/analysis")
    @RequirePermission(LearningPermission.SYSTEM_ADMIN)
    @Operation(summary = "触发公共词本版本关联分析")
    public VocabularyCatalogAnalysisResponse trigger(
            @PathVariable Long catalogVersionId,
            @RequestBody(required = false) VocabularyCatalogAnalysisRequest request) {
        LearningUser user = currentUserContext.requireUser();
        return analysisService.trigger(user.getId(), catalogVersionId, request);
    }
}
