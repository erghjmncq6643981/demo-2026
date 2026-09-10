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
import com.chandler.learning.agent.task.domain.entity.AiAsyncTask;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyCatalogAnalysisResponse;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyCatalogAnalysisConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyImportConstants;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalog;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogVersion;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    @Mock
    private VocabularySemanticReuseService semanticReuseService;

    @Test
    void keepsValidItemsWhenAiResponseCoversOnlyPartOfTheBatch() throws Exception {
        VocabularyCatalogAnalysisService service = new VocabularyCatalogAnalysisService(
                catalogMapper, versionMapper, entryMapper, jobMapper, batchMapper,
                entryAnalysisMapper, asyncTaskService, aiChatService, systemLogService,
                userDisplayNameService, new ObjectMapper(), transactionTemplate, semanticReuseService);

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

    @Test
    void rejectsAnalysisWhenReviewingVersionHasUnconfirmedWarnings() {
        VocabularyCatalogAnalysisService service = new VocabularyCatalogAnalysisService(
                catalogMapper, versionMapper, entryMapper, jobMapper, batchMapper,
                entryAnalysisMapper, asyncTaskService, aiChatService, systemLogService,
                userDisplayNameService, new ObjectMapper(), transactionTemplate, semanticReuseService);

        VocabularyCatalogVersion version = new VocabularyCatalogVersion();
        version.setId(100L);
        version.setCatalogId(200L);
        version.setStatus(VocabularyImportConstants.VERSION_STATUS_REVIEWING);
        version.setWarningCount(3);
        version.setReviewedWarningCount(1);
        version.setDeleted(false);

        when(versionMapper.selectOne(any())).thenReturn(version);

        assertThatThrownBy(() -> service.trigger(1L, 100L, null))
                .isInstanceOf(LearningAssistantException.class)
                .hasMessageContaining("仍有 2 个疑似断词未确认");
    }

    @Test
    void allowsAnalysisWhenReviewingVersionHasAllWarningsConfirmed() {
        VocabularyCatalogAnalysisService service = new VocabularyCatalogAnalysisService(
                catalogMapper, versionMapper, entryMapper, jobMapper, batchMapper,
                entryAnalysisMapper, asyncTaskService, aiChatService, systemLogService,
                userDisplayNameService, new ObjectMapper(), transactionTemplate, semanticReuseService);

        VocabularyCatalogVersion version = new VocabularyCatalogVersion();
        version.setId(100L);
        version.setCatalogId(200L);
        version.setStatus(VocabularyImportConstants.VERSION_STATUS_REVIEWING);
        version.setWarningCount(2);
        version.setReviewedWarningCount(2);
        version.setDeleted(false);

        VocabularyCatalog catalog = new VocabularyCatalog();
        catalog.setId(200L);
        catalog.setName("未发布新词表");
        catalog.setStatus(VocabularyImportConstants.CATALOG_STATUS_DRAFT);
        catalog.setOwnerUserId(1L);
        catalog.setDeleted(false);

        when(versionMapper.selectOne(any())).thenReturn(version);
        when(catalogMapper.selectOne(any())).thenReturn(catalog);
        when(catalogMapper.selectById(200L)).thenReturn(catalog);

        VocabularyCatalogEntry entry = new VocabularyCatalogEntry();
        entry.setId(1001L);
        entry.setCatalogId(200L);
        entry.setCatalogVersionId(100L);
        entry.setPublished(false);
        entry.setDeleted(false);
        when(entryMapper.selectUnanalyzedPublished(100L)).thenReturn(List.of(entry));

        VocabularyCatalogAnalysisJob createdJob = new VocabularyCatalogAnalysisJob();
        createdJob.setId(500L);
        createdJob.setCatalogId(200L);
        createdJob.setCatalogVersionId(100L);
        createdJob.setStatus(VocabularyCatalogAnalysisConstants.STATUS_PENDING);
        createdJob.setTotalCount(1);
        when(jobMapper.selectById(any())).thenReturn(createdJob);
        AiAsyncTask asyncTask = new AiAsyncTask();
        asyncTask.setId(600L);
        when(asyncTaskService.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(asyncTask);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        VocabularyCatalogAnalysisResponse response = service.trigger(1L, 100L, null);

        assertThat(response).isNotNull();
        assertThat(response.getCatalogVersionId()).isEqualTo(100L);
    }
}
