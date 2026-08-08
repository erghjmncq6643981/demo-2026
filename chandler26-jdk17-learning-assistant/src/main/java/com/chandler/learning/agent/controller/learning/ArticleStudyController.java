package com.chandler.learning.agent.controller.learning;

import com.chandler.learning.agent.domain.dto.learning.ArticleStudyRequest;
import com.chandler.learning.agent.domain.dto.learning.ArticleStudyResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningUser;
import com.chandler.learning.agent.service.learning.ArticleStudyService;
import com.chandler.learning.agent.service.learning.AuthService;
import com.chandler.learning.agent.support.LearningConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ArticleStudyController 类。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/learning/articles")
@Tag(name = "文章学习")
public class ArticleStudyController {

    private final AuthService authService;
    private final ArticleStudyService articleStudyService;

    /**
     * 处理 {@code study} 相关业务。
     */
    @PostMapping("/study")
    @Operation(summary = "基于单词本词汇生成文章学习材料")
    public ArticleStudyResponse study(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ArticleStudyRequest request) {
        LearningUser user = authService.requireUser(authorization);
        return articleStudyService.study(user.getId(), request);
    }

    /**
     * 查询 {@code listRecords} 相关业务。
     */
    @GetMapping
    @Operation(summary = "文章学习历史记录")
    public List<ArticleStudyResponse> listRecords(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) Long wordbookId,
            @RequestParam(defaultValue = LearningConstants.Article.DEFAULT_HISTORY_LIMIT_PARAM) Integer limit) {
        LearningUser user = authService.requireUser(authorization);
        return articleStudyService.listRecords(user.getId(), wordbookId, limit);
    }

    /**
     * 查询 {@code detail} 相关业务。
     */
    @GetMapping("/{recordId}")
    @Operation(summary = "文章学习记录详情")
    public ArticleStudyResponse detail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long recordId) {
        LearningUser user = authService.requireUser(authorization);
        return articleStudyService.detail(user.getId(), recordId);
    }
}
