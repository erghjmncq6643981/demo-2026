package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyStudyResponse;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyAliasMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("词汇卡片内存缓存机制测试")
class EnglishVocabularyStudyServiceCacheTest {

    private EnglishVocabularyStudyRecordMapper recordMapper;
    private LearningVocabularyAliasMapper aliasMapper;
    private VocabularyInsightService insightService;
    private EnglishVocabularyStudyService service;

    @BeforeEach
    void setUp() {
        recordMapper = mock(EnglishVocabularyStudyRecordMapper.class);
        aliasMapper = mock(LearningVocabularyAliasMapper.class);
        insightService = mock(VocabularyInsightService.class);
        when(insightService.listTags(any())).thenReturn(List.of());
        when(insightService.listRelations(any())).thenReturn(List.of());

        service = new EnglishVocabularyStudyService(
                recordMapper,
                aliasMapper,
                new EnglishLemmatizer(),
                mock(AiChatService.class),
                new ObjectMapper(),
                insightService,
                mock(VocabularyAudioService.class),
                mock(SystemLogService.class),
                mock(UserDisplayNameService.class));
    }

    @Test
    void detailCachesResultAndAvoidsSubsequentDatabaseLookups() {
        EnglishVocabularyStudyRecord record = new EnglishVocabularyStudyRecord();
        record.setId(101L);
        record.setTerm("pond");
        record.setNormalizedTerm("pond");
        record.setParsedJson("{\"term\":\"pond\",\"lemma\":\"pond\",\"definitions\":[]}");
        record.setLookupCount(1);
        record.setUpdateTime(LocalDateTime.now());

        when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(record);

        // 第一次查询：未命中缓存，从数据库加载并写入卡片缓存
        VocabularyStudyResponse first = service.detail("pond");
        assertThat(first).isNotNull();
        assertThat(first.getTerm()).isEqualTo("pond");
        verify(recordMapper, times(1)).selectOne(any(Wrapper.class));
        verify(insightService, times(1)).listTags(101L);

        // 第二次查询：命中二级缓存，直接返回，不再查询数据库与关联服务
        VocabularyStudyResponse second = service.detail("pond");
        assertThat(second).isSameAs(first);
        verify(recordMapper, times(1)).selectOne(any(Wrapper.class));
        verify(insightService, times(1)).listTags(101L);
    }

    @Test
    void invalidateCardEvictsCache() {
        EnglishVocabularyStudyRecord record = new EnglishVocabularyStudyRecord();
        record.setId(102L);
        record.setTerm("lake");
        record.setNormalizedTerm("lake");
        record.setParsedJson("{\"term\":\"lake\",\"lemma\":\"lake\"}");

        when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(record);

        service.detail("lake");
        verify(recordMapper, times(1)).selectOne(any(Wrapper.class));

        // 淘汰缓存
        service.invalidateCard("lake");

        // 再次查询重新回源
        service.detail("lake");
        verify(recordMapper, times(2)).selectOne(any(Wrapper.class));
    }

    @Test
    void clearCardCacheClearsAllEntries() {
        EnglishVocabularyStudyRecord record1 = new EnglishVocabularyStudyRecord();
        record1.setId(1L);
        record1.setTerm("apple");
        record1.setNormalizedTerm("apple");

        EnglishVocabularyStudyRecord record2 = new EnglishVocabularyStudyRecord();
        record2.setId(2L);
        record2.setTerm("banana");
        record2.setNormalizedTerm("banana");

        when(recordMapper.selectOne(any(Wrapper.class))).thenReturn(record1, record2);

        service.detail("apple");
        service.detail("banana");
        assertThat(service.cardCacheSize()).isEqualTo(2);

        service.clearCardCache();
        assertThat(service.cardCacheSize()).isEqualTo(0);
    }
}
