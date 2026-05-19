package com.chandler.learning.agent.service.learning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.learning.VocabularyRelationResponse;
import com.chandler.learning.agent.domain.dto.learning.VocabularyTagResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningVocabularyRelation;
import com.chandler.learning.agent.domain.entity.learning.LearningVocabularyTag;
import com.chandler.learning.agent.domain.entity.vocabulary.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.mapper.learning.LearningVocabularyRelationMapper;
import com.chandler.learning.agent.mapper.learning.LearningVocabularyTagMapper;
import com.chandler.learning.agent.mapper.vocabulary.EnglishVocabularyStudyRecordMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VocabularyInsightService {

    private static final int MAX_RELATIONS = 80;

    private final LearningVocabularyTagMapper tagMapper;
    private final LearningVocabularyRelationMapper relationMapper;
    private final EnglishVocabularyStudyRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    public void syncInsights(EnglishVocabularyStudyRecord record) {
        if (record == null || record.getId() == null || !StringUtils.hasText(record.getParsedJson())) {
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(record.getParsedJson());
        } catch (Exception ex) {
            return;
        }

        tagMapper.delete(new LambdaQueryWrapper<LearningVocabularyTag>()
                .eq(LearningVocabularyTag::getVocabularyId, record.getId()));
        relationMapper.delete(new LambdaQueryWrapper<LearningVocabularyRelation>()
                .eq(LearningVocabularyRelation::getVocabularyId, record.getId()));

        LocalDateTime now = LocalDateTime.now();
        Map<String, LearningVocabularyTag> tags = new LinkedHashMap<>();
        collectPartOfSpeechTags(root, record, tags, now);
        collectMeaningTopicTags(root, record, tags, now);
        collectArrayTags(root, record, tags, now, "collocations", "collocation", 58);
        collectArrayTags(root, record, tags, now, "word_family", "word_family", 62);
        collectArrayTags(root, record, tags, now, "wordFamily", "word_family", 62);
        addTag(tags, record, "difficulty", inferDifficulty(root, record), inferDifficulty(root, record), 45, now);

        tags.values().forEach(tagMapper::insert);
        collectRelations(root, record, tags.values(), now).forEach(relationMapper::insert);
    }

    public List<VocabularyTagResponse> listTags(Long vocabularyId) {
        return tagMapper.selectList(new LambdaQueryWrapper<LearningVocabularyTag>()
                        .eq(LearningVocabularyTag::getVocabularyId, vocabularyId)
                        .orderByDesc(LearningVocabularyTag::getWeight)
                        .orderByAsc(LearningVocabularyTag::getTagType))
                .stream()
                .map(this::toTagResponse)
                .toList();
    }

    public List<VocabularyRelationResponse> listRelations(String normalizedTerm) {
        if (!StringUtils.hasText(normalizedTerm)) {
            return List.of();
        }
        return relationMapper.selectList(new LambdaQueryWrapper<LearningVocabularyRelation>()
                        .eq(LearningVocabularyRelation::getNormalizedTerm, normalizedTerm)
                        .orderByDesc(LearningVocabularyRelation::getScore)
                        .last("LIMIT 24"))
                .stream()
                .map(this::toRelationResponse)
                .toList();
    }

    private void collectPartOfSpeechTags(JsonNode root, EnglishVocabularyStudyRecord record,
                                         Map<String, LearningVocabularyTag> tags, LocalDateTime now) {
        JsonNode definitions = firstExisting(root, "definitions", "meanings", "translations", "definition");
        if (definitions == null) {
            return;
        }
        for (JsonNode item : iterable(definitions)) {
            String pos = firstText(item, "part_of_speech", "partOfSpeech", "pos", "type", "word_class");
            if (StringUtils.hasText(pos)) {
                addTag(tags, record, "part_of_speech", normalizeValue(pos), pos, 90, now);
            }
        }
    }

    private void collectMeaningTopicTags(JsonNode root, EnglishVocabularyStudyRecord record,
                                         Map<String, LearningVocabularyTag> tags, LocalDateTime now) {
        JsonNode definitions = firstExisting(root, "definitions", "meanings", "translations", "definition");
        Set<String> topics = new LinkedHashSet<>();
        if (definitions != null) {
            for (JsonNode item : iterable(definitions)) {
                String meaning = firstText(item, "meaning", "meaning_cn", "translation", "translation_cn", "cn", "chinese");
                topics.addAll(inferTopics(meaning));
                String english = firstText(item, "english", "meaning_en", "definition", "definition_en", "en");
                topics.addAll(inferTopics(english));
            }
        }
        String memory = firstText(root, "memory_tips", "memoryTips", "tips", "memory");
        topics.addAll(inferTopics(memory));
        for (String topic : topics) {
            addTag(tags, record, "meaning_topic", topic, topic, 70, now);
        }
    }

    private void collectArrayTags(JsonNode root, EnglishVocabularyStudyRecord record, Map<String, LearningVocabularyTag> tags,
                                  LocalDateTime now, String field, String tagType, int weight) {
        JsonNode node = root.get(field);
        if (node == null) {
            return;
        }
        for (JsonNode item : iterable(node)) {
            String text = readableText(item);
            if (StringUtils.hasText(text)) {
                addTag(tags, record, tagType, normalizeValue(text), text, weight, now);
            }
        }
    }

    private List<LearningVocabularyRelation> collectRelations(JsonNode root, EnglishVocabularyStudyRecord record,
                                                              Iterable<LearningVocabularyTag> tags,
                                                              LocalDateTime now) {
        Map<String, LearningVocabularyRelation> relations = new LinkedHashMap<>();
        collectArrayRelations(root, record, relations, now, "synonyms", "synonym", 92);
        collectArrayRelations(root, record, relations, now, "antonyms", "antonym", 82);
        collectArrayRelations(root, record, relations, now, "word_family", "word_family", 78);
        collectArrayRelations(root, record, relations, now, "wordFamily", "word_family", 78);
        collectArrayRelations(root, record, relations, now, "collocations", "collocation", 70);

        for (LearningVocabularyTag tag : tags) {
            if (!List.of("part_of_speech", "meaning_topic").contains(tag.getTagType())) {
                continue;
            }
            List<LearningVocabularyTag> sameTags = tagMapper.selectList(new LambdaQueryWrapper<LearningVocabularyTag>()
                    .eq(LearningVocabularyTag::getTagType, tag.getTagType())
                    .eq(LearningVocabularyTag::getTagValue, tag.getTagValue())
                    .ne(LearningVocabularyTag::getVocabularyId, record.getId())
                    .last("LIMIT 12"));
            for (LearningVocabularyTag sameTag : sameTags) {
                addRelation(relations, record, sameTag.getNormalizedTerm(), "tag_overlap",
                        tag.getTagType() + ":" + tag.getDisplayName(), 60, now);
            }
        }

        return relations.values().stream().limit(MAX_RELATIONS).toList();
    }

    private void collectArrayRelations(JsonNode root, EnglishVocabularyStudyRecord record,
                                       Map<String, LearningVocabularyRelation> relations,
                                       LocalDateTime now, String field, String relationType, int score) {
        JsonNode node = root.get(field);
        if (node == null) {
            return;
        }
        for (JsonNode item : iterable(node)) {
            String text = readableText(item);
            if (StringUtils.hasText(text)) {
                addRelation(relations, record, text, relationType, relationValue(item, relationType), score, now,
                        firstText(item, "part_of_speech", "partOfSpeech", "pos", "type", "word_class"),
                        firstText(item, "meaning", "meaning_cn", "meaningCn", "translation", "translation_cn", "cn", "definition"),
                        StringUtils.hasText(firstText(item, "word", "term", "phrase", "collocation", "text", "value", "name"))
                                ? "parsed_object" : "parsed_text",
                        StringUtils.hasText(firstText(item, "meaning", "meaning_cn", "meaningCn", "translation", "translation_cn", "cn", "definition")) ? score : null);
            }
        }
    }

    private void addTag(Map<String, LearningVocabularyTag> tags, EnglishVocabularyStudyRecord record, String tagType,
                        String tagValue, String displayName, int weight, LocalDateTime now) {
        String cleanValue = normalizeValue(tagValue);
        if (!StringUtils.hasText(cleanValue)) {
            return;
        }
        String key = tagType + ":" + cleanValue;
        LearningVocabularyTag tag = new LearningVocabularyTag();
        tag.setVocabularyId(record.getId());
        tag.setNormalizedTerm(record.getNormalizedTerm());
        tag.setTagType(tagType);
        tag.setTagValue(limit(cleanValue, 128));
        tag.setDisplayName(limit(StringUtils.hasText(displayName) ? displayName.trim() : cleanValue, 128));
        tag.setWeight(weight);
        tag.setSource("parsed_json");
        tag.setCreateTime(now);
        tag.setUpdateTime(now);
        tags.putIfAbsent(key, tag);
    }

    private void addRelation(Map<String, LearningVocabularyRelation> relations, EnglishVocabularyStudyRecord record,
                             String relatedTerm, String relationType, String relationValue, int score, LocalDateTime now) {
        addRelation(relations, record, relatedTerm, relationType, relationValue, score, now, "", "", "", null);
    }

    private void addRelation(Map<String, LearningVocabularyRelation> relations, EnglishVocabularyStudyRecord record,
                             String relatedTerm, String relationType, String relationValue, int score, LocalDateTime now,
                             String parsedPartOfSpeech, String parsedMeaning, String parsedMatchType, Integer parsedMatchScore) {
        String normalizedRelated = normalizeTerm(cleanRelationText(relatedTerm));
        if (!StringUtils.hasText(normalizedRelated) || normalizedRelated.equals(record.getNormalizedTerm())) {
            return;
        }
        EnglishVocabularyStudyRecord relatedRecord = findVocabulary(normalizedRelated);
        CoreMeaning coreMeaning = extractCoreMeaning(relatedRecord);
        String key = relationType + ":" + normalizedRelated;
        LearningVocabularyRelation relation = new LearningVocabularyRelation();
        relation.setVocabularyId(record.getId());
        relation.setRelatedVocabularyId(relatedRecord == null ? null : relatedRecord.getId());
        relation.setNormalizedTerm(record.getNormalizedTerm());
        relation.setRelatedTerm(limit(normalizedRelated, 128));
        relation.setRelationType(relationType);
        relation.setRelationValue(limit(relationValue, 128));
        relation.setRelatedPartOfSpeech(limit(firstNonBlank(parsedPartOfSpeech, coreMeaning.partOfSpeech()), 50));
        relation.setRelatedMeaning(limit(firstNonBlank(parsedMeaning, coreMeaning.meaning()), 512));
        relation.setMatchType(limit(resolveMatchType(parsedMatchType, relatedRecord, parsedMeaning), 50));
        relation.setMatchScore(parsedMatchScore == null ? (relatedRecord == null ? null : 100) : parsedMatchScore);
        relation.setScore(score);
        relation.setSource("parsed_json");
        relation.setCreateTime(now);
        relation.setUpdateTime(now);
        relations.putIfAbsent(key, relation);
    }

    private EnglishVocabularyStudyRecord findVocabulary(String normalizedTerm) {
        return recordMapper.selectOne(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                .eq(EnglishVocabularyStudyRecord::getNormalizedTerm, normalizedTerm)
                .last("LIMIT 1"));
    }

    private Set<String> inferTopics(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> topics = new LinkedHashSet<>();
        addIfContains(topics, lower, "放弃", "抛弃", "leave", "desert", "give up", "abandon");
        addIfContains(topics, lower, "情绪", "悲伤", "desire", "feeling", "grief", "emotion");
        addIfContains(topics, lower, "控制", "约束", "restrain", "control", "inhibition");
        addIfContains(topics, lower, "移动", "旅行", "move", "travel", "go");
        addIfContains(topics, lower, "交流", "说", "tell", "speak", "communicate");
        addIfContains(topics, lower, "学习", "记忆", "learn", "study", "remember");
        addIfContains(topics, lower, "工作", "项目", "计划", "work", "project", "plan");
        addIfContains(topics, lower, "关系", "家庭", "朋友", "family", "friend", "relationship");
        addIfContains(topics, lower, "时间", "频率", "time", "often", "duration");
        return topics;
    }

    private void addIfContains(Set<String> topics, String lower, String topic, String... keywords) {
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                topics.add(topic);
                return;
            }
        }
    }

    private String inferDifficulty(JsonNode root, EnglishVocabularyStudyRecord record) {
        int definitionCount = 0;
        JsonNode definitions = firstExisting(root, "definitions", "meanings", "translations", "definition");
        if (definitions != null) {
            for (JsonNode ignored : iterable(definitions)) {
                definitionCount++;
            }
        }
        int length = record.getNormalizedTerm() == null ? 0 : record.getNormalizedTerm().length();
        if (definitionCount >= 5 || length >= 12) {
            return "hard";
        }
        if (definitionCount >= 3 || length >= 8) {
            return "medium";
        }
        return "easy";
    }

    private JsonNode firstExisting(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... keys) {
        if (node == null) {
            return "";
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            String text = readableText(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String relationValue(JsonNode item, String relationType) {
        String value = firstText(item, "relation", "relation_value", "relationValue", "note", "reason");
        if (StringUtils.hasText(value)) {
            return value;
        }
        if ("collocation".equals(relationType)) {
            return firstText(item, "meaning", "meaning_cn", "meaningCn", "translation", "translation_cn", "cn", "definition");
        }
        return "";
    }

    private Iterable<JsonNode> iterable(JsonNode node) {
        List<JsonNode> list = new ArrayList<>();
        if (node == null) {
            return list;
        }
        if (node.isArray()) {
            node.forEach(list::add);
            return list;
        }
        if (node.isObject()) {
            Iterator<JsonNode> elements = node.elements();
            elements.forEachRemaining(list::add);
            return list;
        }
        list.add(node);
        return list;
    }

    private String readableText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText("");
        }
        if (node.isObject()) {
            for (String key : List.of("word", "term", "phrase", "collocation", "text", "value", "name", "meaning")) {
                String text = firstText(node, key);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : iterable(node)) {
                String text = readableText(item);
                if (StringUtils.hasText(text)) {
                    parts.add(text);
                }
            }
            return String.join("；", parts);
        }
        return "";
    }

    private String cleanRelationText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\[[^]]*]", "")
                .replaceAll("\\s+[-:：]\\s+.*$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public CoreMeaning extractCoreMeaning(EnglishVocabularyStudyRecord record) {
        if (record == null || !StringUtils.hasText(record.getParsedJson())) {
            return CoreMeaning.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(record.getParsedJson());
            JsonNode definitions = firstExisting(root, "definitions", "meanings", "translations", "definition");
            if (definitions == null) {
                return CoreMeaning.empty();
            }
            for (JsonNode item : iterable(definitions)) {
                String partOfSpeech = firstText(item, "part_of_speech", "partOfSpeech", "pos", "type", "word_class");
                String meaning = firstText(item, "meaning", "meaning_cn", "meaningCn", "translation", "translation_cn", "cn", "chinese", "definition_cn", "definitionCn");
                if (!StringUtils.hasText(meaning)) {
                    meaning = firstText(item, "english", "meaning_en", "meaningEn", "definition", "definition_en", "definitionEn", "en");
                }
                if (StringUtils.hasText(partOfSpeech) || StringUtils.hasText(meaning)) {
                    return new CoreMeaning(partOfSpeech, meaning);
                }
            }
        } catch (Exception ignored) {
            return CoreMeaning.empty();
        }
        return CoreMeaning.empty();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String resolveMatchType(String parsedMatchType, EnglishVocabularyStudyRecord relatedRecord, String parsedMeaning) {
        if (StringUtils.hasText(parsedMatchType) && !"parsed_text".equals(parsedMatchType)) {
            return parsedMatchType;
        }
        if (relatedRecord != null) {
            return "cached_exact";
        }
        if (StringUtils.hasText(parsedMeaning)) {
            return "parsed_object";
        }
        return StringUtils.hasText(parsedMatchType) ? parsedMatchType : "parsed_text";
    }

    private String normalizeTerm(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String normalizeValue(String value) {
        return normalizeTerm(value)
                .replaceAll("[：:]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String limit(String value, int length) {
        if (value == null) {
            return null;
        }
        return value.length() <= length ? value : value.substring(0, length);
    }

    private VocabularyTagResponse toTagResponse(LearningVocabularyTag tag) {
        VocabularyTagResponse response = new VocabularyTagResponse();
        response.setId(tag.getId());
        response.setTagType(tag.getTagType());
        response.setTagValue(tag.getTagValue());
        response.setDisplayName(tag.getDisplayName());
        response.setWeight(tag.getWeight());
        return response;
    }

    private VocabularyRelationResponse toRelationResponse(LearningVocabularyRelation relation) {
        VocabularyRelationResponse response = new VocabularyRelationResponse();
        response.setId(relation.getId());
        response.setRelatedVocabularyId(relation.getRelatedVocabularyId());
        response.setRelatedTerm(relation.getRelatedTerm());
        response.setRelationType(relation.getRelationType());
        response.setRelationValue(relation.getRelationValue());
        response.setRelatedPartOfSpeech(relation.getRelatedPartOfSpeech());
        response.setRelatedMeaning(relation.getRelatedMeaning());
        response.setMatchType(relation.getMatchType());
        response.setMatchScore(relation.getMatchScore());
        response.setScore(relation.getScore());
        return response;
    }

    public record CoreMeaning(String partOfSpeech, String meaning) {
        static CoreMeaning empty() {
            return new CoreMeaning("", "");
        }
    }
}
