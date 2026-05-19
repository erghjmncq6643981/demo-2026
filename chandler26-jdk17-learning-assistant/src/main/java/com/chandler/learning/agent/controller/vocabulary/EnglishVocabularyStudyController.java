package com.chandler.learning.agent.controller.vocabulary;

import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyStudyRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyStudyResponse;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyBestMatchResponse;
import com.chandler.learning.agent.service.vocabulary.EnglishVocabularyStudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 英语词汇学习控制器。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/english/vocabularies")
@Tag(name = "英语词汇学习")
public class EnglishVocabularyStudyController {

    private final EnglishVocabularyStudyService vocabularyStudyService;

    @PostMapping("/study")
    @Operation(summary = "查询或生成词汇学习结果")
    public VocabularyStudyResponse study(@Valid @RequestBody VocabularyStudyRequest request) {
        return vocabularyStudyService.study(request);
    }

    @GetMapping("/{term}/best-match")
    @Operation(summary = "查询用户输入的最接近词汇缓存")
    public VocabularyBestMatchResponse bestMatch(@PathVariable String term) {
        return vocabularyStudyService.bestMatch(term);
    }

    @GetMapping("/{term}")
    @Operation(summary = "查询词汇学习缓存")
    public VocabularyStudyResponse detail(@PathVariable String term) {
        return vocabularyStudyService.detail(term);
    }
}
