package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.reading.api.ArticleStudyAnswerRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleStudyServicePracticeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void scoresThreeReadingQuestions() throws Exception {
        JsonNode parsed = objectMapper.readTree("""
                {"practice":[
                  {"correct_answer":"A plan"},
                  {"correct_answer":"Her confidence"},
                  {"correct_answer":"Careful action"}
                ]}
                """);

        ArticleStudyService.PracticeScore result = ArticleStudyService.scorePractice(parsed, List.of(
                answer(0, "A plan"),
                answer(1, "her confidence"),
                answer(2, "Fear")
        ));

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.answered()).isEqualTo(3);
        assertThat(result.correct()).isEqualTo(2);
        assertThat(result.score()).isEqualTo(67);
    }

    @Test
    void reportsUnansweredQuestions() throws Exception {
        JsonNode parsed = objectMapper.readTree("""
                {"practice":[{"answer":"One"},{"answer":"Two"},{"answer":"Three"}]}
                """);

        ArticleStudyService.PracticeScore result = ArticleStudyService.scorePractice(
                parsed, List.of(answer(1, "Two")));

        assertThat(result.answered()).isEqualTo(1);
        assertThat(result.correct()).isEqualTo(1);
        assertThat(result.score()).isEqualTo(33);
    }

    private ArticleStudyAnswerRequest answer(int index, String value) {
        ArticleStudyAnswerRequest answer = new ArticleStudyAnswerRequest();
        answer.setQuestionIndex(index);
        answer.setAnswer(value);
        return answer;
    }
}
