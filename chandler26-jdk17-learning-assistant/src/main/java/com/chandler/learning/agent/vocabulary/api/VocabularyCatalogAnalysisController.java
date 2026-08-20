package com.chandler.learning.agent.vocabulary.api;

import com.chandler.learning.agent.vocabulary.api.VocabularyCatalogAnalysisRequest;
import com.chandler.learning.agent.vocabulary.api.VocabularyCatalogAnalysisResponse;
import com.chandler.learning.agent.identity.domain.LearningUser;
import com.chandler.learning.agent.identity.application.AuthService;
import com.chandler.learning.agent.vocabulary.application.VocabularyCatalogAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公共词本关联分析接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vocabulary-catalogs")
@Tag(name = "公共词本关联分析")
public class VocabularyCatalogAnalysisController {

    private final AuthService authService;
    private final VocabularyCatalogAnalysisService analysisService;

    @GetMapping("/{catalogVersionId}/analysis")
    @Operation(summary = "查询公共词本版本关联分析状态")
    public VocabularyCatalogAnalysisResponse detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long catalogVersionId) {
        LearningUser user = authService.requireAdmin(authorization);
        return analysisService.detail(user.getId(), catalogVersionId);
    }

    @PostMapping("/{catalogVersionId}/analysis")
    @Operation(summary = "触发公共词本版本关联分析")
    public VocabularyCatalogAnalysisResponse trigger(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long catalogVersionId,
            @RequestBody(required = false) VocabularyCatalogAnalysisRequest request) {
        LearningUser user = authService.requireAdmin(authorization);
        return analysisService.trigger(user.getId(), catalogVersionId, request);
    }
}
