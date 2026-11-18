package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chandler.learning.agent.learning.application.LearningActivityService;
import com.chandler.learning.agent.learning.domain.enums.LearningActivityEventType;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.api.request.RecentSearchReviewSubmitRequest;
import com.chandler.learning.agent.vocabulary.api.response.RecentSearchReviewQueueResponse;
import com.chandler.learning.agent.vocabulary.api.response.ReviewSubmitResponse;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyLookupHistory;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyLookupHistoryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyLookupHistoryServiceTest {

    @Mock
    private LearningVocabularyLookupHistoryMapper lookupHistoryMapper;
    @Mock
    private EnglishVocabularyStudyRecordMapper studyRecordMapper;
    @Mock
    private LearningWordbookEntryMapper wordbookEntryMapper;
    @Mock
    private LearningActivityService activityService;
    @Mock
    private SystemLogService systemLogService;

    private VocabularyLookupHistoryService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, EnglishVocabularyStudyRecord.class);
        TableInfoHelper.initTableInfo(assistant, LearningWordbookEntry.class);
        service = new VocabularyLookupHistoryService(
                lookupHistoryMapper, studyRecordMapper, wordbookEntryMapper, activityService, systemLogService);
    }

    @Test
    void returnsTodayAndYesterdayWordsWhenCountMeetsMinimumThreshold() {
        List<LearningVocabularyLookupHistory> fiveItems = List.of(
                lookup(1L, 101L, "apple", 2),
                lookup(2L, 102L, "banana", 1),
                lookup(3L, 103L, "cucumber", 3),
                lookup(4L, 104L, "ginger", 1),
                lookup(5L, 105L, "strawberry", 2)
        );
        when(lookupHistoryMapper.selectSinceTime(eq(1001L), any(LocalDateTime.class), anyInt()))
                .thenReturn(fiveItems);

        EnglishVocabularyStudyRecord card = new EnglishVocabularyStudyRecord();
        card.setId(101L);
        card.setNormalizedTerm("apple");
        card.setParsedJson("{\"term\":\"apple\",\"phonetic\":{\"uk\":\"/ˈæp.əl/\"}}");
        when(studyRecordMapper.selectBatchIds(anyCollection())).thenReturn(List.of(card));
        when(studyRecordMapper.selectList(any())).thenReturn(List.of());

        LearningWordbookEntry ownedEntry = new LearningWordbookEntry();
        ownedEntry.setId(501L);
        ownedEntry.setWordbookId(9001L);
        ownedEntry.setNormalizedTerm("apple");
        ownedEntry.setStatus("vague");
        ownedEntry.setNote("脆甜水果");
        when(wordbookEntryMapper.selectList(any())).thenReturn(List.of(ownedEntry));

        RecentSearchReviewQueueResponse response = service.listRecentSearchReviewQueue(1001L, 2, 5, 20);

        assertThat(response.getMode()).isEqualTo("today_yesterday");
        assertThat(response.getRecentTwoDayCount()).isEqualTo(5);
        assertThat(response.getTotalCount()).isEqualTo(5);
        assertThat(response.getItems()).hasSize(5);
        assertThat(response.getItems().get(0).getEntryId()).isEqualTo(501L);
        assertThat(response.getItems().get(0).getNote()).isEqualTo("脆甜水果");
        assertThat(response.getItems().get(0).getParsed()).contains("apple");
        verify(lookupHistoryMapper, never()).selectRecent(any(), anyInt());
    }

    @Test
    void fallsBackToRecentTwentySearchedWordsWhenTwoDayCountIsLessThanFive() {
        when(lookupHistoryMapper.selectSinceTime(eq(1001L), any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(lookup(1L, 101L, "apple", 1), lookup(2L, 102L, "berry", 2)));
        when(lookupHistoryMapper.selectRecent(eq(1001L), eq(20)))
                .thenReturn(List.of(
                        lookup(1L, 101L, "apple", 1),
                        lookup(2L, 102L, "berry", 2),
                        lookup(3L, 103L, "harvest", 1),
                        lookup(4L, 104L, "crisp", 4)
                ));
        when(studyRecordMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(studyRecordMapper.selectList(any())).thenReturn(List.of());
        when(wordbookEntryMapper.selectList(any())).thenReturn(List.of());

        RecentSearchReviewQueueResponse response = service.listRecentSearchReviewQueue(1001L, 2, 5, 20);

        assertThat(response.getMode()).isEqualTo("recent_fallback");
        assertThat(response.getRecentTwoDayCount()).isEqualTo(2);
        assertThat(response.getTotalCount()).isEqualTo(4);
        assertThat(response.getHintMessage()).contains("仅搜索 2 个词").contains("最近搜索的 4 个词");
        verify(lookupHistoryMapper).selectRecent(1001L, 20);
    }

    @Test
    void submitsLookupOnlyReviewAndRecordsActivity() {
        RecentSearchReviewSubmitRequest req = new RecentSearchReviewSubmitRequest();
        req.setLookupId(88L);
        req.setTerm("Strawberry");
        req.setResult("remembered");
        req.setReviewAction("quick_pass");
        req.setDurationSeconds(5);

        ReviewSubmitResponse res = service.submitLookupOnlyReview(1001L, req);

        assertThat(res.getNormalizedTerm()).isEqualTo("strawberry");
        assertThat(res.getMasteryScore()).isEqualTo(70);
        assertThat(res.getReviewStage()).isEqualTo(1);
        verify(lookupHistoryMapper).markReviewed(eq(1001L), eq("strawberry"), any(LocalDateTime.class));
        verify(activityService).record(
                eq(1001L), eq(LearningActivityEventType.WORD_REVIEWED), any(LocalDateTime.class),
                eq(null), eq(null), eq(null), eq(88L), eq(1), eq(5), eq("remembered"), any());
    }

    private LearningVocabularyLookupHistory lookup(Long id, Long vocabId, String term, int count) {
        LearningVocabularyLookupHistory item = new LearningVocabularyLookupHistory();
        item.setId(id);
        item.setUserId(1001L);
        item.setVocabularyId(vocabId);
        item.setTerm(term);
        item.setNormalizedTerm(term);
        item.setLookupSource("study");
        item.setLookupCount(count);
        item.setLastLookupTime(LocalDateTime.now());
        return item;
    }
}
