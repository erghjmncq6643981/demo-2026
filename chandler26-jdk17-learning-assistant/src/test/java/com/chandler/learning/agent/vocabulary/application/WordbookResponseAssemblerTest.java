package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.vocabulary.api.response.WordbookEntryResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntrySummaryResponse;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 单词本响应装配器单元测试。
 */
class WordbookResponseAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LearningWordbookMapper wordbookMapper;
    private LearningWordbookEntryMapper entryMapper;
    private EnglishVocabularyStudyRecordMapper vocabularyMapper;
    private VocabularyInsightService vocabularyInsightService;
    private WordbookResponseAssembler assembler;

    @BeforeEach
    void setUp() {
        wordbookMapper = mock(LearningWordbookMapper.class);
        entryMapper = mock(LearningWordbookEntryMapper.class);
        vocabularyMapper = mock(EnglishVocabularyStudyRecordMapper.class);
        vocabularyInsightService = mock(VocabularyInsightService.class);
        assembler = new WordbookResponseAssembler(
                wordbookMapper,
                entryMapper,
                vocabularyMapper,
                vocabularyInsightService,
                objectMapper);
    }

    @Test
    @DisplayName("结构化词卡解析：支持嵌套对象格式音标")
    void shouldExtractNestedObjectPhonetic() {
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setId(1001L);
        entry.setWordbookId(2001L);
        entry.setTerm("accident");
        entry.setNormalizedTerm("accident");
        entry.setSnapshotParsedJson("""
                {
                  "term": "accident",
                  "phonetic": { "uk": "/ˈæksɪdənt/", "us": "/ˈæksɪdənt/" },
                  "definitions": [
                    { "pos": "noun", "meaning": "意外事故；车祸" },
                    { "pos": "noun", "meaning": "偶然；意外" }
                  ],
                  "examples": [{ "sentence": "It was an accident." }]
                }
                """);

        WordbookEntryResponse response = assembler.toEntryResponse(entry);
        assertThat(response.getPhonetic()).isEqualTo("/ˈæksɪdənt/");
        assertThat(response.getMeaningText()).isEqualTo("noun. 意外事故；车祸； noun. 偶然；意外");

        WordbookEntrySummaryResponse summary = assembler.toSummaryResponse(entry);
        assertThat(summary.getPhonetic()).isEqualTo("/ˈæksɪdənt/");
        assertThat(summary.getMeaningText()).isEqualTo("noun. 意外事故；车祸； noun. 偶然；意外");
    }

    @Test
    @DisplayName("结构化词卡解析：支持基础词表导入的字符串格式音标")
    void shouldExtractStringPhonetic() {
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setId(1002L);
        entry.setWordbookId(2001L);
        entry.setTerm("account");
        entry.setNormalizedTerm("account");
        entry.setSnapshotParsedJson("""
                {
                  "term": "account",
                  "phonetic": "[əˈkaʊnt]",
                  "definitions": [{ "meaning": "n. 账户，账目" }]
                }
                """);

        WordbookEntryResponse response = assembler.toEntryResponse(entry);
        assertThat(response.getPhonetic()).isEqualTo("[əˈkaʊnt]");
        assertThat(response.getMeaningText()).isEqualTo("n. 账户，账目");
    }

    @Test
    @DisplayName("结构化词卡解析：支持点号扁平键格式音标")
    void shouldExtractDotKeyPhonetic() {
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setId(1003L);
        entry.setWordbookId(2001L);
        entry.setTerm("blast");
        entry.setNormalizedTerm("blast");
        entry.setSnapshotParsedJson("""
                {
                  "term": "blast",
                  "phonetic.uk": "/blɑːst/",
                  "phonetic.us": "/blæst/",
                  "definitions": [{ "pos": "n.", "meaning": "爆炸" }]
                }
                """);

        WordbookEntryResponse response = assembler.toEntryResponse(entry);
        assertThat(response.getPhonetic()).isEqualTo("/blɑːst/");
        assertThat(response.getMeaningText()).isEqualTo("n. 爆炸");
    }

    @Test
    @DisplayName("词卡快照回退：个人快照为空时能从公共词卡记录提取音标和释义")
    void shouldFallbackToVocabularyRecordWhenSnapshotMissing() {
        LearningWordbookEntry entry = new LearningWordbookEntry();
        entry.setId(1004L);
        entry.setWordbookId(2001L);
        entry.setTerm("accidental");
        entry.setNormalizedTerm("accidental");
        entry.setVocabularyId(3001L);
        entry.setSnapshotParsedJson(null);

        EnglishVocabularyStudyRecord record = new EnglishVocabularyStudyRecord();
        record.setId(3001L);
        record.setTerm("accidental");
        record.setNormalizedTerm("accidental");
        record.setParsedJson("""
                {
                  "term": "accidental",
                  "phonetic": { "uk": "/ˌæksɪˈdentl/", "us": "/ˌæksɪˈdentl/" },
                  "definitions": [{ "pos": "adj.", "meaning": "意外的；偶然的" }],
                  "examples": [{ "sentence": "The fire was accidental." }]
                }
                """);
        when(vocabularyMapper.selectById(3001L)).thenReturn(record);

        WordbookEntryResponse response = assembler.toEntryResponse(entry);
        assertThat(response.getPhonetic()).isEqualTo("/ˌæksɪˈdentl/");
        assertThat(response.getMeaningText()).isEqualTo("adj. 意外的；偶然的");
    }
}
