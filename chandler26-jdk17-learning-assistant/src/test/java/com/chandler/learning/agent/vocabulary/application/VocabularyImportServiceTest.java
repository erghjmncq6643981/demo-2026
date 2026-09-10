package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyImportResponse;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalog;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyImportJob;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyCatalogVersionMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.VocabularyImportJobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyImportServiceTest {

    @Mock
    private MarkdownVocabularyParser markdownParser;
    @Mock
    private VocabularyCatalogMapper catalogMapper;
    @Mock
    private VocabularyCatalogVersionMapper versionMapper;
    @Mock
    private VocabularyCatalogEntryMapper catalogEntryMapper;
    @Mock
    private VocabularyImportJobMapper importJobMapper;
    @Mock
    private LearningWordbookMapper wordbookMapper;
    @Mock
    private LearningWordbookEntryMapper wordbookEntryMapper;
    @Mock
    private LearningWordProgressService progressService;
    @Mock
    private SystemLogService systemLogService;
    @Mock
    private UserDisplayNameService userDisplayNameService;

    private VocabularyImportService service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, VocabularyCatalogEntry.class);
        TableInfoHelper.initTableInfo(assistant, VocabularyImportJob.class);
        TableInfoHelper.initTableInfo(assistant, VocabularyCatalog.class);

        service = new VocabularyImportService(
                markdownParser,
                catalogMapper,
                versionMapper,
                catalogEntryMapper,
                importJobMapper,
                wordbookMapper,
                wordbookEntryMapper,
                progressService,
                systemLogService,
                userDisplayNameService,
                new ObjectMapper()
        );
    }

    @Test
    void detailAppliesWarningOnlyFilterCorrectly() {
        VocabularyImportJob job = new VocabularyImportJob();
        job.setId(100L);
        job.setCatalogId(200L);
        job.setCatalogVersionId(300L);
        job.setTotalCount(440);
        job.setWarningCount(0);
        job.setReviewedWarningCount(0);
        job.setStatus("draft");

        VocabularyCatalog catalog = new VocabularyCatalog();
        catalog.setId(200L);
        catalog.setName("小升初440");

        when(importJobMapper.selectOne(any())).thenReturn(job);
        when(catalogMapper.selectOne(any())).thenReturn(catalog);

        Page<VocabularyCatalogEntry> emptyPage = new Page<>(1, 100);
        emptyPage.setRecords(List.of());
        when(catalogEntryMapper.selectPage(any(), any())).thenReturn(emptyPage);

        VocabularyImportResponse response = service.detail(1L, 100L, true, null, 1, 100);

        assertThat(response.getFilteredTotal()).isEqualTo(0L);
        assertThat(response.getItems()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<VocabularyCatalogEntry>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(catalogEntryMapper).selectPage(any(), captor.capture());

        LambdaQueryWrapper<VocabularyCatalogEntry> wrapper = captor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertThat(sqlSegment).contains("suspicious");
    }
}
