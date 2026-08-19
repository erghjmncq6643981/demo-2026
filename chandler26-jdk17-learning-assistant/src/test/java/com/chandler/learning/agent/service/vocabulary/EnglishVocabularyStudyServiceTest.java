package com.chandler.learning.agent.service.vocabulary;

import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.mapper.vocabulary.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.service.learning.VocabularyInsightService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EnglishVocabularyStudyServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EnglishVocabularyStudyService service = new EnglishVocabularyStudyService(
            mock(EnglishVocabularyStudyRecordMapper.class),
            mock(AiChatService.class),
            objectMapper,
            mock(VocabularyInsightService.class),
            mock(SystemLogService.class),
            mock(UserDisplayNameService.class));

    @Test
    void keepsScalarMemoryTipsWhenNormalizingDeepSeekCard() throws Exception {
        String content = """
                {
                  "term": "blanket",
                  "definitions": [{"part_of_speech": "noun", "meaning": "毯子"}],
                  "examples": [{"sentence": "She wrapped herself in a warm blanket."}],
                  "collocations": [{"phrase": "electric blanket", "meaning": "电热毯"}],
                  "memory_tips": "联想 blanket 像一层覆盖物。"
                }
                """;

        String normalized = ReflectionTestUtils.invokeMethod(service, "extractJson", content, "blanket");
        JsonNode root = objectMapper.readTree(normalized);

        assertThat(root.path("term").asText()).isEqualTo("blanket");
        assertThat(root.path("definitions").isArray()).isTrue();
        assertThat(root.path("memory_tips").isTextual()).isTrue();
        assertThat(root.path("memory_tips").asText()).isEqualTo("联想 blanket 像一层覆盖物。");
    }

    @Test
    void wrapsSingleObjectArrayFieldWithoutCreatingSelfReference() throws Exception {
        String content = """
                {
                  "term": "blanket",
                  "definitions": {"part_of_speech": "noun", "meaning": "毯子"},
                  "examples": [],
                  "collocations": [],
                  "memory_tips": "记忆提示"
                }
                """;

        String normalized = ReflectionTestUtils.invokeMethod(service, "extractJson", content, "blanket");
        JsonNode root = objectMapper.readTree(normalized);

        assertThat(root.path("definitions").isArray()).isTrue();
        assertThat(root.path("definitions")).hasSize(1);
        assertThat(root.path("definitions").get(0).path("meaning").asText()).isEqualTo("毯子");
    }
}
