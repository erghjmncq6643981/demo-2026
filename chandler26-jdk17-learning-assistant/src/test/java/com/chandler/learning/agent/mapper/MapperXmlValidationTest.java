package com.chandler.learning.agent.mapper;

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
                "com.chandler.learning.agent.mapper.learning.LearningPlanMapper.claimGenerationLock")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.mapper.learning.LearningPlanUnitMapper.selectMaxUnitNoIncludingDeleted"))
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
                "com.chandler.learning.agent.mapper.learning.LearningPlanMapper.releaseGenerationLock")).isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogAnalysisBatchMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogEntryAnalysisMapper.insertBatch"))
                .isTrue();
        assertThat(configuration.hasStatement(
                "com.chandler.learning.agent.mapper.vocabulary.VocabularyCatalogEntryMapper.selectUnanalyzedPublished"))
                .isTrue();
    }
}
