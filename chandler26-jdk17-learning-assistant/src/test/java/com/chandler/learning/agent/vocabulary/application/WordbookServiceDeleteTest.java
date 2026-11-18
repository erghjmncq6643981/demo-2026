package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.learning.application.LearningActivityService;
import com.chandler.learning.agent.learning.application.LearningReviewService;
import com.chandler.learning.agent.learning.application.ReviewSchedulePolicy;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 单词本逻辑删除单元测试。
 */
class WordbookServiceDeleteTest {

    private LearningWordbookMapper wordbookMapper;
    private LearningWordbookEntryMapper entryMapper;
    private LearningReviewService reviewService;
    private EnglishVocabularyStudyRecordMapper vocabularyMapper;
    private EnglishVocabularyStudyService vocabularyStudyService;
    private VocabularyInsightService vocabularyInsightService;
    private SystemLogService systemLogService;
    private UserDisplayNameService userDisplayNameService;
    private ObjectMapper objectMapper;
    private ReviewSchedulePolicy reviewSchedulePolicy;
    private WordbookResponseAssembler responseAssembler;
    private AiAsyncTaskService aiAsyncTaskService;
    private LearningActivityService activityService;
    private WordbookService wordbookService;

    @BeforeEach
    void setUp() {
        wordbookMapper = mock(LearningWordbookMapper.class);
        entryMapper = mock(LearningWordbookEntryMapper.class);
        reviewService = mock(LearningReviewService.class);
        vocabularyMapper = mock(EnglishVocabularyStudyRecordMapper.class);
        vocabularyStudyService = mock(EnglishVocabularyStudyService.class);
        vocabularyInsightService = mock(VocabularyInsightService.class);
        systemLogService = mock(SystemLogService.class);
        userDisplayNameService = mock(UserDisplayNameService.class);
        objectMapper = new ObjectMapper();
        reviewSchedulePolicy = mock(ReviewSchedulePolicy.class);
        responseAssembler = mock(WordbookResponseAssembler.class);
        aiAsyncTaskService = mock(AiAsyncTaskService.class);
        activityService = mock(LearningActivityService.class);

        wordbookService = new WordbookService(
                wordbookMapper,
                entryMapper,
                reviewService,
                vocabularyMapper,
                vocabularyStudyService,
                vocabularyInsightService,
                systemLogService,
                userDisplayNameService,
                objectMapper,
                reviewSchedulePolicy,
                responseAssembler,
                aiAsyncTaskService,
                activityService
        );
    }

    @Test
    @DisplayName("删除词条时应调用 entryMapper.deleteById 触发 MyBatis-Plus 逻辑删除")
    void shouldCallDeleteByIdWhenDeletingEntry() {
        Long userId = 1001L;
        Long entryId = 2001L;
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setId(entryId);
        entry.setUserId(userId);
        entry.setNormalizedTerm("a multitude of");
        entry.setDeleted(false);

        when(entryMapper.selectById(entryId)).thenReturn(entry);
        when(userDisplayNameService.userName(userId)).thenReturn("testUser");

        wordbookService.deleteEntry(userId, entryId);

        verify(entryMapper).deleteById(entryId);
        verify(entryMapper, never()).updateById(any(LearningWordbookEntry.class));
    }

    @Test
    @DisplayName("删除单词本时应调用 wordbookMapper.deleteById 和 entryMapper.delete 触发逻辑删除")
    void shouldCallDeleteByIdWhenDeletingWordbook() {
        Long userId = 1001L;
        Long wordbookId = 3001L;
        LearningWordbook wordbook = new LearningWordbook();
        wordbook.setId(wordbookId);
        wordbook.setUserId(userId);
        wordbook.setName("自考英语");
        wordbook.setIsDefault(false);
        wordbook.setDeleted(false);

        when(wordbookMapper.selectById(wordbookId)).thenReturn(wordbook);
        when(entryMapper.selectCount(any())).thenReturn(0L);
        when(userDisplayNameService.userName(userId)).thenReturn("testUser");

        wordbookService.deleteWordbook(userId, wordbookId);

        verify(wordbookMapper).deleteById(wordbookId);
        verify(wordbookMapper, never()).updateById(any(LearningWordbook.class));
        verify(entryMapper).delete(any());
    }
}
