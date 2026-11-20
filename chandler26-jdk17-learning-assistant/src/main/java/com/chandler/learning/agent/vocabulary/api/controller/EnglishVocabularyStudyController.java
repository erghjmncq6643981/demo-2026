package com.chandler.learning.agent.vocabulary.api.controller;

import com.chandler.learning.agent.identity.domain.entity.LearningUser;
import com.chandler.learning.agent.security.CurrentUserContext;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyStudyRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyStudyResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyBestMatchResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularySuggestionResponse;
import com.chandler.learning.agent.vocabulary.application.EnglishVocabularyStudyService;
import com.chandler.learning.agent.vocabulary.application.VocabularyLookupHistoryService;
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
    private final VocabularyLookupHistoryService lookupHistoryService;
    private final CurrentUserContext currentUserContext;
    private final com.chandler.learning.agent.vocabulary.application.WordbookEntryQueryService wordbookEntryQueryService;

    /** 查询或生成词汇学习结果。 */
    @PostMapping("/study")
    @Operation(summary = "查询或生成词汇学习结果")
    public VocabularyStudyResponse study(@Valid @RequestBody VocabularyStudyRequest request) {
        LearningUser user = currentUserContext.requireUser();
        VocabularyStudyResponse response = vocabularyStudyService.study(user.getId(), request);
        if (response != null) {
            lookupHistoryService.recordLookup(
                    user.getId(),
                    response.getId(),
                    response.getTerm(),
                    response.getNormalizedTerm(),
                    request.getLookupSource());
            response.setUserEntry(wordbookEntryQueryService.findUserEntryByTerm(user.getId(), response.getNormalizedTerm()));
        }
        return response;
    }

    /**
     * 查询词汇自动补全与搜索联想建议列表。
     */
    @GetMapping("/suggestions")
    @Operation(summary = "查询词汇自动补全与联想建议")
    public List<VocabularySuggestionResponse> suggestions(@RequestParam("keyword") String keyword) {
        return vocabularyStudyService.suggestions(keyword);
    }

    /** 查询用户输入的最接近词汇缓存。 */
    @GetMapping("/{term}/best-match")
    @Operation(summary = "查询用户输入的最接近词汇缓存")
    public VocabularyBestMatchResponse bestMatch(@PathVariable String term) {
        VocabularyBestMatchResponse response = vocabularyStudyService.bestMatch(term);
        if (response != null && response.getRecord() != null) {
            currentUserContext.findUser().ifPresent(user ->
                    response.getRecord().setUserEntry(wordbookEntryQueryService.findUserEntryByTerm(user.getId(), response.getRecord().getNormalizedTerm())));
        }
        return response;
    }

    /** 查询词汇学习缓存。 */
    @GetMapping("/{term}")
    @Operation(summary = "查询词汇学习缓存")
    public VocabularyStudyResponse detail(@PathVariable String term) {
        VocabularyStudyResponse response = vocabularyStudyService.detail(term);
        if (response != null) {
            currentUserContext.findUser().ifPresent(user -> {
                lookupHistoryService.recordLookup(
                        user.getId(),
                        response.getId(),
                        response.getTerm(),
                        response.getNormalizedTerm(),
                        VocabularyLookupHistoryService.SOURCE_QUICK_LOOKUP);
                response.setUserEntry(wordbookEntryQueryService.findUserEntryByTerm(user.getId(), response.getNormalizedTerm()));
            });
        }
        return response;
    }
}
