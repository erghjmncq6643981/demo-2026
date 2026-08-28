package com.chandler.learning.agent.learning.application;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** 词汇挑战的题目规范化、答案判定和拼写评分辅助组件。 */
@Component
@RequiredArgsConstructor
public class LearningPlanAssessmentSupport {

    private final ObjectMapper objectMapper;

    /** 将外部检查类型限制在系统支持的枚举值内。 */
    public String normalizeAssessmentType(String type) {
        String normalized = normalize(type);
        if (LearningConstants.ScenePlan.ASSESSMENT_MEANING_CHOICE.equals(normalized)
                || LearningConstants.ScenePlan.ASSESSMENT_COPY_TYPING.equals(normalized)
                || LearningConstants.ScenePlan.ASSESSMENT_MEANING_SPELLING.equals(normalized)) {
            return normalized;
        }
        throw assessmentInvalid("不支持的检查类型: " + type);
    }

    /** 读取场景词卡中模型生成的可接受拼写集合。 */
    public List<String> acceptedSpellings(JsonNode word, String term) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(term);
        JsonNode accepted = node(word, "accepted_spellings", "acceptedSpellings");
        if (accepted != null && accepted.isArray()) {
            accepted.forEach(item -> {
                if (item.isTextual() && StringUtils.hasText(item.asText())) {
                    result.add(item.asText().trim());
                }
            });
        }
        if (term.contains("-")) {
            result.add(term.replace('-', ' '));
        }
        return List.copyOf(result);
    }

    /** 兼容模型返回的不完整或非标准四选一题目，保证前端始终获得可作答题目。 */
    public JsonNode ensureMeaningQuestion(JsonNode wordNode, String term,
                                           JsonNode question, String defaultMeaning) {
        com.fasterxml.jackson.databind.node.ObjectNode questionObject;
        if (question instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
            questionObject = obj;
        } else {
            questionObject = objectMapper.createObjectNode();
            if (wordNode instanceof com.fasterxml.jackson.databind.node.ObjectNode wordObj) {
                wordObj.set("meaning_question", questionObject);
            }
        }
        if (!questionObject.hasNonNull("question")
                || !StringUtils.hasText(questionObject.get("question").asText())) {
            questionObject.put("question", "「" + term + "」在语境中的主要含义是？");
        }

        JsonNode optionsNode = node(questionObject, "options", "choices", "selections", "items", "option_list");
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode;
        if (optionsNode instanceof com.fasterxml.jackson.databind.node.ArrayNode arr && !arr.isEmpty()) {
            arrayNode = arr;
        } else if (optionsNode instanceof com.fasterxml.jackson.databind.node.ObjectNode obj && !obj.isEmpty()) {
            arrayNode = objectMapper.createArrayNode();
            obj.elements().forEachRemaining(arrayNode::add);
        } else {
            arrayNode = objectMapper.createArrayNode();
            String correct = StringUtils.hasText(defaultMeaning) ? defaultMeaning.trim() : term + " 的含义";
            arrayNode.add(correct);
            arrayNode.add("状态或性质");
            arrayNode.add("行动或过程");
            arrayNode.add("关联与影响");
        }
        while (arrayNode.size() > 4) {
            arrayNode.remove(arrayNode.size() - 1);
        }
        List<String> genericDistractors = List.of("状态或性质", "行动或过程", "关联与影响", "其他相关表达");
        int distractorIndex = 0;
        while (arrayNode.size() < 4) {
            arrayNode.add(genericDistractors.get(distractorIndex % genericDistractors.size()));
            distractorIndex++;
        }
        questionObject.set("options", arrayNode);

        String correct = text(questionObject, "correct_answer", "correctAnswer", "answer");
        if (!StringUtils.hasText(correct)) {
            correct = StringUtils.hasText(defaultMeaning) ? defaultMeaning.trim() : arrayNode.get(0).asText();
            questionObject.put("correct_answer", correct);
        }
        boolean contained = false;
        for (JsonNode option : arrayNode) {
            if (normalizeAnswer(option.asText()).equals(normalizeAnswer(correct))) {
                contained = true;
                break;
            }
        }
        if (!contained && correct.length() == 1) {
            char ch = Character.toUpperCase(correct.charAt(0));
            int index = ch >= 'A' && ch <= 'D' ? ch - 'A' : ch >= '1' && ch <= '4' ? ch - '1' : -1;
            if (index >= 0 && index < arrayNode.size()) {
                questionObject.put("correct_answer", arrayNode.get(index).asText());
                contained = true;
            }
        }
        if (!contained) {
            String normalizedCorrect = normalizeAnswer(correct);
            int bestMatchIndex = -1;
            for (int i = 0; i < arrayNode.size(); i++) {
                String option = normalizeAnswer(arrayNode.get(i).asText());
                if (option.contains(normalizedCorrect) || normalizedCorrect.contains(option)) {
                    bestMatchIndex = i;
                    break;
                }
            }
            if (bestMatchIndex >= 0) {
                questionObject.put("correct_answer", arrayNode.get(bestMatchIndex).asText());
            } else {
                arrayNode.set(0, objectMapper.getNodeFactory().textNode(correct));
                questionObject.put("correct_answer", correct);
            }
        }
        return questionObject;
    }

    public JsonNode readTree(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw assessmentInvalid("检查题数据已损坏，请重新生成当前场景");
        }
    }

    public List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    public double spellingAccuracy(String answer, List<String> accepted) {
        int bestDistance = accepted.stream()
                .map(this::normalizeSpelling)
                .mapToInt(candidate -> levenshtein(answer, candidate))
                .min().orElse(answer.length());
        int maxLength = Math.max(1, Math.max(answer.length(), accepted.stream()
                .map(this::normalizeSpelling).mapToInt(String::length).max().orElse(1)));
        return Math.max(0D, Math.round((1D - (double) bestDistance / maxLength) * 10_000D) / 100D);
    }

    public String normalizeAnswer(String value) {
        return normalize(value).replaceAll("[，。；;,.!?！？]$", "");
    }

    public String normalizeSpelling(String value) {
        return normalize(value).replace('’', '\'');
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int i = 0; i <= right.length(); i++) previous[i] = i;
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private JsonNode node(JsonNode source, String... keys) {
        if (source == null) return null;
        for (String key : keys) {
            JsonNode value = source.path(key);
            if (!value.isMissingNode() && !value.isNull()) return value;
        }
        return null;
    }

    private String text(JsonNode source, String... keys) {
        JsonNode value = node(source, keys);
        return value != null && value.isValueNode() && StringUtils.hasText(value.asText())
                ? value.asText().trim() : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private LearningAssistantException assessmentInvalid(String message) {
        return LearningAssistantException.badRequest(
                LearningConstants.ErrorCode.LEARNING_ASSESSMENT_INVALID, message);
    }
}
