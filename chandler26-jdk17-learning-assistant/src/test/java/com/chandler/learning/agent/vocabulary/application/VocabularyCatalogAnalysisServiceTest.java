package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.ai.chat.application.codec.AiSceneResponse;
import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogAnalysisBatch;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogAnalysisJob;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogAnalysisBatchMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogAnalysisJobMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogEntryAnalysisMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogVersionMapper;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.task.application.AiAsyncTaskService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class VocabularyCatalogAnalysisServiceTest {

    @Mock
    private VocabularyCatalogMapper catalogMapper;
    @Mock
    private VocabularyCatalogVersionMapper versionMapper;
    @Mock
    private VocabularyCatalogEntryMapper entryMapper;
    @Mock
    private VocabularyCatalogAnalysisJobMapper jobMapper;
    @Mock
    private VocabularyCatalogAnalysisBatchMapper batchMapper;
    @Mock
    private VocabularyCatalogEntryAnalysisMapper entryAnalysisMapper;
    @Mock
    private AiAsyncTaskService asyncTaskService;
    @Mock
    private AiChatService aiChatService;
    @Mock
    private SystemLogService systemLogService;
    @Mock
    private UserDisplayNameService userDisplayNameService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @Test
    void keepsValidItemsWhenAiResponseCoversOnlyPartOfTheBatch() throws Exception {
        VocabularyCatalogAnalysisService service = new VocabularyCatalogAnalysisService(
                catalogMapper, versionMapper, entryMapper, jobMapper, batchMapper,
                entryAnalysisMapper, asyncTaskService, aiChatService, systemLogService,
                userDisplayNameService, new ObjectMapper(), transactionTemplate);

        VocabularyCatalogAnalysisJob job = new VocabularyCatalogAnalysisJob();
        job.setId(10L);
        job.setUserId(20L);
        job.setCatalogId(30L);
        job.setCatalogVersionId(40L);
        job.setAnalysisVersion(1);

        VocabularyCatalogAnalysisBatch batch = new VocabularyCatalogAnalysisBatch();
        batch.setBatchNo(1);

        VocabularyCatalogEntry first = new VocabularyCatalogEntry();
        first.setId(1L);
        VocabularyCatalogEntry second = new VocabularyCatalogEntry();
        second.setId(2L);

        AgentChatResponse response = new AgentChatResponse();
        String content = """
                {"entries":[{"entry_id":1,"primary_group_code":"travel","primary_group_name":"旅行","domain":"travel","sub_topic":"airport","tags":[],"related_entry_ids":[],"difficulty_level":"medium","confidence":0.9}]}
                """;
        response.setContent(content);
        response.setStructuredResponse(new AiSceneResponse(
                AiInvocationScene.VOCABULARY_CATALOG_ANALYSIS,
                new ObjectMapper().readTree(content), content, "deepseek-json", "raw", List.of()));

        VocabularyCatalogAnalysisService.AnalysisParseResult result = service.parseAnalyses(
                job, batch, List.of(first, second), response);

        assertThat(result.analyses()).hasSize(1);
        assertThat(result.analyses().get(0).getCatalogEntryId()).isEqualTo(1L);
        assertThat(result.unresolvedEntryIds()).containsExactly(2L);
    }
}
