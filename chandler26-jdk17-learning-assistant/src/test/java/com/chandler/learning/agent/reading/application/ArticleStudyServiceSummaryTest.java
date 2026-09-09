package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.reading.api.response.ArticleStudySummaryResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningArticleStudyRecord;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningArticleStudyRecordMapper;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.chandler.learning.agent.learning.application.LearningActivityService;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("语境精读历史摘要及标题解析测试")
class ArticleStudyServiceSummaryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ArticleStudyService service = new ArticleStudyService(
            mock(LearningArticleStudyRecordMapper.class),
            mock(WordbookService.class),
            mock(AiChatService.class),
            mock(LearningWordProgressService.class),
            mock(SystemLogService.class),
            mock(UserDisplayNameService.class),
            objectMapper,
            mock(LearningActivityService.class)
    );

    @Test
    @DisplayName("摘要组装应提取 parsedJson 中的文章标题")
    void shouldExtractTitleInSummaryResponse() {
        LearningArticleStudyRecord record = new LearningArticleStudyRecord();
        record.setId(1001L);
        record.setWordbookId(2001L);
        record.setParsedJson("""
                {
                  "title": "The Ocean's Call for Change",
                  "article": "The ocean has always been a source of wonder and life."
                }
                """);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());

        ArticleStudySummaryResponse summary = ReflectionTestUtils.invokeMethod(service, "toSummaryResponse", record);

        assertThat(summary).isNotNull();
        assertThat(summary.getId()).isEqualTo(1001L);
        assertThat(summary.getTitle()).isEqualTo("The Ocean's Call for Change");
    }

    @Test
    @DisplayName("缺少 parsedJson 时摘要标题应为 null 以便前端降级")
    void shouldReturnNullTitleWhenParsedJsonMissing() {
        LearningArticleStudyRecord record = new LearningArticleStudyRecord();
        record.setId(1002L);
        record.setWordbookId(2001L);
        record.setParsedJson(null);

        ArticleStudySummaryResponse summary = ReflectionTestUtils.invokeMethod(service, "toSummaryResponse", record);

        assertThat(summary).isNotNull();
        assertThat(summary.getTitle()).isNull();
    }
}
