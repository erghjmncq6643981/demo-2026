package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyStudyRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyStudyResponse;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyAlias;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyAliasMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("词卡缓存形态容错与词形还原测试")
class EnglishVocabularyStudyServiceAliasTest {

    private EnglishVocabularyStudyRecordMapper recordMapper;
    private LearningVocabularyAliasMapper aliasMapper;
    private EnglishLemmatizer lemmatizer;
    private AiChatService aiChatService;
    private VocabularyInsightService insightService;
    private SystemLogService systemLogService;
    private UserDisplayNameService userDisplayNameService;
    private ObjectMapper objectMapper;
    private EnglishVocabularyStudyService service;

    @BeforeEach
    void setUp() {
        recordMapper = Mockito.mock(EnglishVocabularyStudyRecordMapper.class);
        aliasMapper = Mockito.mock(LearningVocabularyAliasMapper.class);
        lemmatizer = new EnglishLemmatizer();
        aiChatService = Mockito.mock(AiChatService.class);
        insightService = Mockito.mock(VocabularyInsightService.class);
        VocabularyAudioService audioService = Mockito.mock(VocabularyAudioService.class);
        systemLogService = Mockito.mock(SystemLogService.class);
        userDisplayNameService = Mockito.mock(UserDisplayNameService.class);
        objectMapper = new ObjectMapper();

        service = new EnglishVocabularyStudyService(
                recordMapper,
                aliasMapper,
                lemmatizer,
                aiChatService,
                objectMapper,
                insightService,
                audioService,
                systemLogService,
                userDisplayNameService);
    }

    @Test
    @DisplayName("当直接命中别名索引时应返回原形词卡并标记 isAliasHit")
    void shouldHitCacheViaAliasIndex() {
        LearningVocabularyAlias alias = new LearningVocabularyAlias();
        alias.setId(1001L);
        alias.setVocabularyId(2001L);
        alias.setAliasTerm("running");
        alias.setNormalizedAlias("running");
        alias.setLemma("run");
        alias.setNormalizedLemma("run");

        EnglishVocabularyStudyRecord record = new EnglishVocabularyStudyRecord();
        record.setId(2001L);
        record.setTerm("run");
        record.setNormalizedTerm("run");
        record.setParsedJson("""
                {
                  "term": "run",
                  "lemma": "run",
                  "inflections": ["runs", "running", "ran"],
                  "definitions": [{"part_of_speech": "verb", "meaning": "跑"}]
                }
                """);
        record.setLookupCount(5);

        when(aliasMapper.findByNormalizedAlias("running")).thenReturn(alias);
        when(recordMapper.selectById(2001L)).thenReturn(record);

        VocabularyStudyRequest request = new VocabularyStudyRequest();
        request.setTerm("running");

        VocabularyStudyResponse response = service.study(request);

        assertThat(response).isNotNull();
        assertThat(response.getQueriedTerm()).isEqualTo("running");
        assertThat(response.getTerm()).isEqualTo("run");
        assertThat(response.getLemma()).isEqualTo("run");
        assertThat(response.getCacheHit()).isTrue();
        assertThat(response.getIsAliasHit()).isTrue();
        assertThat(response.getInflections()).containsExactly("runs", "running", "ran");
    }

    @Test
    @DisplayName("当别名表无记录但通过词形还原器推导出原形且命中缓存时，应返回词卡并触发洞察同步")
    void shouldHitCacheViaLemmatizerFallback() {
        // 查询 "apples" -> 还原推导出 "apple"
        EnglishVocabularyStudyRecord appleRecord = new EnglishVocabularyStudyRecord();
        appleRecord.setId(3001L);
        appleRecord.setTerm("apple");
        appleRecord.setNormalizedTerm("apple");
        appleRecord.setParsedJson("""
                {
                  "term": "apple",
                  "lemma": "apple",
                  "inflections": ["apples"],
                  "definitions": [{"part_of_speech": "noun", "meaning": "苹果"}]
                }
                """);
        appleRecord.setLookupCount(3);

        // 第一调用 findByNormalizedTerm("apples") 返回 null，后续候选词 findByNormalizedTerm("apple") 返回 appleRecord
        when(recordMapper.selectOne(any())).thenReturn(null, appleRecord);
        when(aliasMapper.findByNormalizedAlias(any())).thenReturn(null);

        VocabularyStudyRequest request = new VocabularyStudyRequest();
        request.setTerm("apples");

        VocabularyStudyResponse response = service.study(request);

        assertThat(response).isNotNull();
        assertThat(response.getQueriedTerm()).isEqualTo("apples");
        assertThat(response.getTerm()).isEqualTo("apple");
        assertThat(response.getLemma()).isEqualTo("apple");
        assertThat(response.getCacheHit()).isTrue();
        assertThat(response.getIsAliasHit()).isTrue();
        assertThat(response.getInflections()).containsExactly("apples");
        verify(insightService, Mockito.atLeastOnce()).syncInsights(appleRecord);
    }

    @Test
    @DisplayName("独立单词（如 modest）绝不能误命中不相关的原形词（如 mode）")
    void shouldNotMistakeModestForMode() {
        EnglishVocabularyStudyRecord modeRecord = new EnglishVocabularyStudyRecord();
        modeRecord.setId(4001L);
        modeRecord.setTerm("mode");
        modeRecord.setNormalizedTerm("mode");
        modeRecord.setParsedJson("""
                {
                  "term": "mode",
                  "lemma": "mode",
                  "inflections": ["modes"],
                  "definitions": [{"part_of_speech": "noun", "meaning": "模式"}]
                }
                """);
        modeRecord.setLookupCount(2);

        // findByNormalizedTerm("modest") 返回 null, findByNormalizedTerm("mode") 返回 modeRecord
        when(recordMapper.selectOne(any())).thenReturn(null, modeRecord);
        when(aliasMapper.findByNormalizedAlias(any())).thenReturn(null);

        EnglishVocabularyStudyRecord result = service.findRecord("modest");
        assertThat(result).isNull();
    }
}
