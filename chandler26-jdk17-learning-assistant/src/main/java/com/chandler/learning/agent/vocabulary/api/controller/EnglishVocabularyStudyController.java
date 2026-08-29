package com.chandler.learning.agent.vocabulary.api.controller;

import com.chandler.learning.agent.vocabulary.api.request.VocabularyStudyRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyStudyResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyBestMatchResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularySuggestionResponse;
import com.chandler.learning.agent.vocabulary.application.EnglishVocabularyStudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 英语词汇学习控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/english/vocabularies")
@Tag(name = "英语词汇学习")
public class EnglishVocabularyStudyController {

    private final EnglishVocabularyStudyService vocabularyStudyService;

    /**
     * 处理 {@code study} 相关业务。
     */
    @PostMapping("/study")
    @Operation(summary = "查询或生成词汇学习结果")
    public VocabularyStudyResponse study(@Valid @RequestBody VocabularyStudyRequest request) {
        return vocabularyStudyService.study(request);
    }

    /**
     * 查询词汇自动补全与搜索联想建议列表。
     */
    @GetMapping("/suggestions")
    @Operation(summary = "查询词汇自动补全与联想建议")
    public List<VocabularySuggestionResponse> suggestions(@RequestParam("keyword") String keyword) {
        return vocabularyStudyService.suggestions(keyword);
    }

    /**
     * 处理 {@code bestMatch} 相关业务。
     */
    @GetMapping("/{term}/best-match")
    @Operation(summary = "查询用户输入的最接近词汇缓存")
    public VocabularyBestMatchResponse bestMatch(@PathVariable String term) {
        return vocabularyStudyService.bestMatch(term);
    }

    /**
     * 查询 {@code detail} 相关业务。
     */
    @GetMapping("/{term}")
    @Operation(summary = "查询词汇学习缓存")
    public VocabularyStudyResponse detail(@PathVariable String term) {
        return vocabularyStudyService.detail(term);
    }
}
