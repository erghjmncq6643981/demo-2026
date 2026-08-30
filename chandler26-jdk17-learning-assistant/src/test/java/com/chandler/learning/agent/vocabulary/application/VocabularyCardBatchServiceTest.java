package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.learning.application.LearningPlanAccessService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCardGenerationJobItem;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordProgressMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCardGenerationJobItemMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCardGenerationJobMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VocabularyCardBatchServiceTest {

    @Test
    void savesGeneratedCardsAndItemStatesInBatch() throws Exception {
        EnglishVocabularyStudyRecordMapper vocabularyMapper = mock(EnglishVocabularyStudyRecordMapper.class);
        VocabularyCardGenerationJobItemMapper itemMapper = mock(VocabularyCardGenerationJobItemMapper.class);
        LearningWordProgressMapper progressMapper = mock(LearningWordProgressMapper.class);
        VocabularyInsightService insightService = mock(VocabularyInsightService.class);
        VocabularyCardBatchService service = service(vocabularyMapper, itemMapper, progressMapper, insightService);

        VocabularyCardGenerationJobItem first = item(1L, 11L, "airport");
        VocabularyCardGenerationJobItem second = item(2L, 12L, "boarding");
        Map<VocabularyCardGenerationJobItem, JsonNode> generated = new LinkedHashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        generated.put(first, objectMapper.readTree("{\"term\":\"airport\",\"definitions\":[]}"));
        generated.put(second, objectMapper.readTree("{\"term\":\"boarding\",\"definitions\":[]}"));
        when(vocabularyMapper.selectList(any())).thenReturn(List.of(
                vocabulary(101L, "airport"), vocabulary(102L, "boarding")));

        AgentChatResponse response = new AgentChatResponse();
        response.setModelProvider("deepseek");
        response.setModelName("deepseek-v4-flash");
        ReflectionTestUtils.invokeMethod(service, "saveCards", generated, response, 2);

        ArgumentCaptor<List<EnglishVocabularyStudyRecord>> cards = ArgumentCaptor.forClass(List.class);
        verify(vocabularyMapper).insertBatchIgnore(cards.capture());
        verify(vocabularyMapper, never()).insert(any(EnglishVocabularyStudyRecord.class));
        assertThat(cards.getValue()).hasSize(2);
        verify(insightService).syncInsightsBatch(any());

        ReflectionTestUtils.invokeMethod(service, "updateItemStatuses", List.of(first, second), "failed", "provider error");

        verify(itemMapper).updateBatch(List.of(first, second));
        verify(progressMapper).updateCardStatusBatch(any(), org.mockito.ArgumentMatchers.eq("failed"), any());
        assertThat(first.getStatus()).isEqualTo("failed");
        assertThat(second.getStatus()).isEqualTo("failed");
    }

    private VocabularyCardBatchService service(EnglishVocabularyStudyRecordMapper vocabularyMapper,
                                               VocabularyCardGenerationJobItemMapper itemMapper,
                                               LearningWordProgressMapper progressMapper,
                                               VocabularyInsightService insightService) {
        return new VocabularyCardBatchService(
                mock(VocabularyCardGenerationJobMapper.class), itemMapper, vocabularyMapper,
                mock(com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyAliasMapper.class),
                mock(LearningPlanAccessService.class), progressMapper, mock(AiChatService.class),
                mock(WordbookService.class), insightService, mock(SystemLogService.class),
                mock(UserDisplayNameService.class), new ObjectMapper(), mock(TransactionTemplate.class),
                mock(ApplicationEventPublisher.class), mock(AiAsyncTaskService.class));
    }

    private VocabularyCardGenerationJobItem item(Long id, Long progressId, String term) {
        VocabularyCardGenerationJobItem item = new VocabularyCardGenerationJobItem();
        item.setId(id);
        item.setWordProgressId(progressId);
        item.setTerm(term);
        item.setNormalizedTerm(term);
        item.setAttemptCount(0);
        return item;
    }

    private EnglishVocabularyStudyRecord vocabulary(Long id, String term) {
        EnglishVocabularyStudyRecord record = new EnglishVocabularyStudyRecord();
        record.setId(id);
        record.setTerm(term);
        record.setNormalizedTerm(term);
        record.setParsedJson("{\"term\":\"" + term + "\"}");
        return record;
    }
}
