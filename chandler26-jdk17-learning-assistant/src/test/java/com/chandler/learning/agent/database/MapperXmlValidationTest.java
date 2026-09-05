package com.chandler.learning.agent.database;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapperXmlValidationTest {

    @Test
    void loadsVocabularyAnalysisAndLearningPlanStatements() throws Exception {
        Configuration configuration = new Configuration();
        List<String> resources = List.of(
                "mapper/LearningPlanMapper.xml",
                "mapper/LearningPlanUnitMapper.xml",
                "mapper/LearningPlanUnitEntryMapper.xml",
                "mapper/AiChatMessageMapper.xml",
                "mapper/AiChatSessionMapper.xml",
                "mapper/AiModelCallRecordMapper.xml",
                "mapper/AiAsyncTaskStepMapper.xml",
                "mapper/LearningSceneRelatedWordMapper.xml",
                "mapper/VocabularyCatalogAnalysisBatchMapper.xml",
                "mapper/VocabularyCatalogEntryAnalysisMapper.xml",
                "mapper/VocabularyCatalogEntryMapper.xml",
                "mapper/LearningWordProgressMapper.xml",
                "mapper/LearningWordbookEntryMapper.xml",
                "mapper/LearningWordbookMapper.xml",
                "mapper/EnglishVocabularyStudyRecordMapper.xml",
                "mapper/LearningVocabularyTagMapper.xml",
                "mapper/LearningVocabularyRelationMapper.xml",
                "mapper/LearningUserPreferenceMapper.xml",
                "mapper/VocabularyCardGenerationJobItemMapper.xml",
                "mapper/LearningSystemLogMapper.xml",
                "mapper/LearningSystemLogOutboxMapper.xml",
                "mapper/LearningReviewRecordMapper.xml");

        for (String resource : resources) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource,
                        configuration.getSqlFragments()).parse();
            }
        }

        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper.claimGenerationLock")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper.selectMaxUnitNoIncludingDeleted"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitMapper.selectUnitsWithMaterial"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper.selectEntriesWithProgress"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanUnitEntryMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.ai.chat.infrastructure.mapper.AiChatMessageMapper.selectNextSequence"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.ai.chat.infrastructure.mapper.AiChatSessionMapper.selectSessionSummaries"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.ai.chat.infrastructure.mapper.AiModelCallRecordMapper.selectUsageSummaries"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskStepMapper.claim")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskStepMapper.renew")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskStepMapper.recoverExpired")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.task.infrastructure.mapper.AiAsyncTaskStepMapper.insertBatch")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.mapper.LearningSceneRelatedWordMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.mapper.LearningPlanMapper.releaseGenerationLock")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.mapper.LearningReviewRecordMapper.selectPassedAssessmentTypesBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogAnalysisBatchMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogEntryAnalysisMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogEntryMapper.selectUnanalyzedPublished"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordProgressMapper.updateBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper.upsertLearningBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper.updateVocabularyCardBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookMapper.selectWordbookSummaries"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCardGenerationJobItemMapper.selectProgress"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper.insertBatchIgnore"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyTagMapper.selectByVocabularyIds"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyRelationMapper.selectByNormalizedTerms"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.identity.infrastructure.mapper.LearningUserPreferenceMapper.upsertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.system.infrastructure.mapper.LearningSystemLogOutboxMapper.claimPendingBatch"))
                .isTrue();
    }
}
