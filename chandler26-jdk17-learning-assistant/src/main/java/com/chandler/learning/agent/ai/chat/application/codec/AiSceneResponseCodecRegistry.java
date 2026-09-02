package com.chandler.learning.agent.ai.chat.application.codec;

import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.ai.gateway.parser.AiStructuredResponseParseResult;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 场景响应契约注册表。
 * <p>
 * 供应商解析器只负责把模型文本恢复为 JSON；本注册表负责按调用场景解包根对象、
 * 归一字段别名并校验场景必填字段。业务服务直接消费这里产出的结构化根节点，禁止再次解析响应文本。
 */
@Component
@RequiredArgsConstructor
public class AiSceneResponseCodecRegistry {

    private static final List<String> ROOT_WRAPPERS = List.of(
            "scene", "data", "result", "unit", "material", "card", "vocabulary", "word", "item");

    private final ObjectMapper objectMapper;

    /** 将供应商解析结果转换为指定调用场景的稳定结构。 */
    public AiSceneResponse decode(AiInvocationScene invocationScene, AiStructuredResponseParseResult parsed) {
        if (invocationScene == null || !invocationScene.isStructuredResponse()) {
            return null;
        }
        JsonNode root = parsed == null || parsed.root() == null ? null : parsed.root().deepCopy();
        if (root == null || !root.isObject()) {
            throw LearningAssistantException.badRequest(LearningErrorCode.AI_RESPONSE_PARSE_FAILED);
        }
        normalizeAliases(root, invocationScene.getRequiredRootFields());
        if (!containsRequiredFields(root, invocationScene.getRequiredRootFields())) {
            root = unwrap(root, invocationScene.getRequiredRootFields());
        }
        normalizeAliases(root, invocationScene.getRequiredRootFields());
        JsonNode contractRoot = root;
        List<String> missingFields = invocationScene.getRequiredRootFields().stream()
                .filter(field -> !hasValue(contractRoot, field))
                .toList();
        if (!missingFields.isEmpty()) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.AI_RESPONSE_PARSE_FAILED,
                    "AI 返回内容缺少必要字段：" + String.join("、", missingFields));
        }
        try {
            return new AiSceneResponse(invocationScene, root, objectMapper.writeValueAsString(root),
                    parsed.parserName(), parsed.parseStage(), List.copyOf(parsed.repairs()));
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningErrorCode.JSON_SERIALIZE_FAILED,
                    "AI 场景响应标准化失败",
                    ex);
        }
    }

    private JsonNode unwrap(JsonNode root, List<String> requiredFields) {
        for (String wrapper : ROOT_WRAPPERS) {
            JsonNode candidate = root.path(wrapper);
            if (!candidate.isObject()) {
                continue;
            }
            JsonNode normalized = candidate.deepCopy();
            normalizeAliases(normalized, requiredFields);
            if (containsRequiredFields(normalized, requiredFields)) {
                return normalized;
            }
        }
        return root;
    }

    private boolean containsRequiredFields(JsonNode root, List<String> requiredFields) {
        return requiredFields.stream().allMatch(field -> hasValue(root, field));
    }

    private boolean hasValue(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return !value.isMissingNode() && !value.isNull();
    }

    private static final List<String> COMMON_NORMALIZED_FIELDS = List.of(
            "term", "lemma", "inflections", "phonetic", "definitions", "examples",
            "collocations", "memory_tips", "synonyms", "antonyms", "word_family",
            "title", "learning_text", "translation", "vocabulary", "related_words",
            "entries", "cards", "article", "vocabulary_focus", "grammar_points", "practice");

    private void normalizeAliases(JsonNode root, List<String> requiredFields) {
        if (!(root instanceof ObjectNode objectNode)) {
            return;
        }
        for (String field : requiredFields) {
            applyAlias(objectNode, field);
        }
        for (String field : COMMON_NORMALIZED_FIELDS) {
            applyAlias(objectNode, field);
        }
        // 若 definitions 为单一对象，自动包裹为数组
        JsonNode definitions = objectNode.get("definitions");
        if (definitions != null && definitions.isObject()) {
            objectNode.putArray("definitions").add(definitions);
        }
    }

    private void applyAlias(ObjectNode objectNode, String field) {
        if (hasValue(objectNode, field)) {
            return;
        }
        for (String alias : fieldAliases(field)) {
            JsonNode value = objectNode.get(alias);
            if (value != null && !value.isNull() && !value.isMissingNode()) {
                objectNode.set(field, value);
                break;
            }
        }
    }

    private List<String> fieldAliases(String field) {
        return switch (field) {
            case "term" -> List.of("word", "lemma", "headword", "target_word", "targetWord", "name");
            case "vocabulary" -> List.of("words", "vocabulary_list", "vocabularies", "word_list");
            case "learning_text" -> List.of("learningText", "text", "article", "content", "passage", "scene_text");
            case "translation" -> List.of("chinese_translation", "chinese", "text_translation", "translation_text");
            case "title" -> List.of("scene_title", "unit_title", "topic");
            case "entries" -> List.of("words", "items", "catalog_entries");
            case "cards" -> List.of("vocabulary", "words", "list");
            case "definitions" -> List.of("meaning", "meanings", "definition", "translations", "explanation", "explanations", "senses");
            case "examples" -> List.of("example_sentences", "example", "sentences", "exampleSentences", "sample_sentences", "samples");
            case "collocations" -> List.of("phrases", "collocation", "common_phrases", "commonPhrases", "collocates", "combinations", "expressions");
            case "memory_tips" -> List.of("memoryTips", "tips", "memory_tip", "memoryTip", "mnemonic", "mnemonics", "memory", "memory_method", "memory_aid", "memory_advice", "study_tips", "study_tip", "note", "notes", "remember_tip", "rememberTips");
            case "article" -> List.of("content", "text", "learning_text");
            case "vocabulary_focus" -> List.of("vocabularyFocus", "vocabulary", "words", "core_words");
            case "grammar_points" -> List.of("grammarPoints", "grammar", "points");
            case "practice" -> List.of("exercises", "questions");
            default -> List.of();
        };
    }
}
