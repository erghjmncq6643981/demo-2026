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
                "mapper/AiChatMessageMapper.xml",
                "mapper/AiChatSessionMapper.xml",
                "mapper/AiModelCallRecordMapper.xml",
                "mapper/AiAsyncTaskStepMapper.xml",
                "mapper/LearningSceneRelatedWordMapper.xml",
                "mapper/VocabularyCatalogAnalysisBatchMapper.xml",
                "mapper/VocabularyCatalogEntryAnalysisMapper.xml",
                "mapper/VocabularyCatalogEntryMapper.xml");

        for (String resource : resources) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource,
                        configuration.getSqlFragments()).parse();
            }
        }

        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.LearningPlanMapper.claimGenerationLock")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.LearningPlanUnitMapper.selectMaxUnitNoIncludingDeleted"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.ai.chat.infrastructure.AiChatMessageMapper.selectNextSequence"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.ai.chat.infrastructure.AiChatSessionMapper.selectSessionSummaries"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.ai.chat.infrastructure.AiModelCallRecordMapper.selectUsageSummaries"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.task.infrastructure.AiAsyncTaskStepMapper.claim")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.task.infrastructure.AiAsyncTaskStepMapper.recoverExpired")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.LearningSceneRelatedWordMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.learning.infrastructure.LearningPlanMapper.releaseGenerationLock")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.VocabularyCatalogAnalysisBatchMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.VocabularyCatalogEntryAnalysisMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.vocabulary.infrastructure.VocabularyCatalogEntryMapper.selectUnanalyzedPublished"))
                .isTrue();
    }
}
