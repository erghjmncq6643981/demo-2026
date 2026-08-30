package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyRelationResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyTagResponse;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyAlias;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyRelation;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyTag;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.enums.VocabularyDifficulty;
import com.chandler.learning.agent.vocabulary.domain.enums.VocabularyMatchType;
import com.chandler.learning.agent.vocabulary.domain.enums.VocabularyRelationType;
import com.chandler.learning.agent.vocabulary.domain.enums.VocabularyTagType;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyAliasMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyRelationMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyTagMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyConstants;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyInsightConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 词汇应用服务。
 */
@Service
@RequiredArgsConstructor
public class VocabularyInsightService {

    private static final int WRITE_BATCH_SIZE = 200;

    private final LearningVocabularyTagMapper tagMapper;
    private final LearningVocabularyRelationMapper relationMapper;
    private final LearningVocabularyAliasMapper aliasMapper;
    private final EnglishVocabularyStudyRecordMapper recordMapper;
    private final EnglishLemmatizer lemmatizer;
    private final ObjectMapper objectMapper;

    /** 从结构化词卡同步标签和语义关系。 */
    public void syncInsights(EnglishVocabularyStudyRecord record) {
        syncInsightsBatch(record == null ? List.of() : List.of(record));
    }

    /**
     * 批量同步词卡洞察。所有关联词缓存先一次性预取，避免在解析关联关系时逐词查询。
     */
    public void syncInsightsBatch(Collection<EnglishVocabularyStudyRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<InsightInput> inputs = new ArrayList<>();
        Set<String> relatedTerms = new LinkedHashSet<>();
        for (EnglishVocabularyStudyRecord record : records) {
            if (record == null || record.getId() == null || !StringUtils.hasText(record.getParsedJson())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(record.getParsedJson());
                inputs.add(new InsightInput(record, root));
                relatedTerms.addAll(extractRelatedTerms(root));
            } catch (Exception ignored) {
                // 单条词卡 JSON 损坏不应阻断同批其他词卡。
            }
        }
        if (inputs.isEmpty()) {
            return;
        }
        Map<String, EnglishVocabularyStudyRecord> relatedRecords = relatedTerms.isEmpty()
                ? Map.of()
                : recordMapper.selectList(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                        .in(EnglishVocabularyStudyRecord::getNormalizedTerm, relatedTerms)
                        .eq(EnglishVocabularyStudyRecord::getDeleted, false))
                .stream().collect(java.util.stream.Collectors.toMap(
                        EnglishVocabularyStudyRecord::getNormalizedTerm, value -> value, (left, right) -> left));
        Set<Long> vocabularyIds = inputs.stream().map(input -> input.record().getId()).collect(java.util.stream.Collectors.toSet());
        tagMapper.physicalDeleteByVocabularyIds(vocabularyIds);
        relationMapper.physicalDeleteByVocabularyIds(vocabularyIds);
        aliasMapper.physicalDeleteByVocabularyIds(vocabularyIds);
        LocalDateTime now = LocalDateTime.now();
        List<LearningVocabularyTag> allTags = new ArrayList<>();
        List<LearningVocabularyRelation> allRelations = new ArrayList<>();
        List<LearningVocabularyAlias> allAliases = new ArrayList<>();
        for (InsightInput input : inputs) {
            Map<String, LearningVocabularyTag> tags = new LinkedHashMap<>();
            collectPartOfSpeechTags(input.root(), input.record(), tags, now);
            collectMeaningTopicTags(input.root(), input.record(), tags, now);
            collectArrayTags(input.root(), input.record(), tags, now, "collocations", VocabularyTagType.COLLOCATION.getCode(), VocabularyInsightConstants.TAG_WEIGHT_COLLOCATION);
            collectArrayTags(input.root(), input.record(), tags, now, "word_family", VocabularyTagType.WORD_FAMILY.getCode(), VocabularyInsightConstants.TAG_WEIGHT_WORD_FAMILY);
            collectArrayTags(input.root(), input.record(), tags, now, "wordFamily", VocabularyTagType.WORD_FAMILY.getCode(), VocabularyInsightConstants.TAG_WEIGHT_WORD_FAMILY);
            addTag(tags, input.record(), VocabularyTagType.DIFFICULTY.getCode(), inferDifficulty(input.root(), input.record()), inferDifficulty(input.root(), input.record()),
                    VocabularyInsightConstants.TAG_WEIGHT_DIFFICULTY, now);
            allTags.addAll(tags.values());
            allRelations.addAll(collectRelations(input.root(), input.record(), now, relatedRecords::get));
            allAliases.addAll(collectAliases(input.root(), input.record(), now));
        }
        insertChunks(allTags, tagMapper::insertBatch);
        insertChunks(allRelations, relationMapper::insertBatch);
        insertChunks(allAliases, aliasMapper::insertBatch);
    }

    /** 查询词汇标签列表。 */
    public List<VocabularyTagResponse> listTags(Long vocabularyId) {
        return listTagsByVocabularyIds(List.of(vocabularyId)).getOrDefault(vocabularyId, List.of());
    }

    /** 批量查询标签并按词卡 ID 分组。 */
    public Map<Long, List<VocabularyTagResponse>> listTagsByVocabularyIds(Collection<Long> vocabularyIds) {
        Set<Long> ids = vocabularyIds == null ? Set.of() : vocabularyIds.stream()
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tagMapper.selectByVocabularyIds(ids).stream().collect(java.util.stream.Collectors.groupingBy(
                LearningVocabularyTag::getVocabularyId, LinkedHashMap::new,
                java.util.stream.Collectors.mapping(this::toTagResponse, java.util.stream.Collectors.toList())));
    }

    /** 查询词汇的语义关系列表。 */
    public List<VocabularyRelationResponse> listRelations(String normalizedTerm) {
        if (!StringUtils.hasText(normalizedTerm)) {
            return List.of();
        }
        return listRelationsByNormalizedTerms(List.of(normalizedTerm)).getOrDefault(normalizedTerm, List.of());
    }

    /** 批量查询关联词并一次性补齐来源词和关联词音标。 */
    public Map<String, List<VocabularyRelationResponse>> listRelationsByNormalizedTerms(Collection<String> normalizedTerms) {
        Set<String> terms = normalizedTerms == null ? Set.of() : normalizedTerms.stream()
                .filter(StringUtils::hasText).map(this::normalizeTerm)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (terms.isEmpty()) {
            return Map.of();
        }
        List<LearningVocabularyRelation> relations = relationMapper.selectByNormalizedTerms(terms);
        RecordLookup lookup = loadRecords(
                relations.stream().flatMap(relation -> java.util.stream.Stream.of(
                                relation.getVocabularyId(), relation.getRelatedVocabularyId()))
                        .filter(java.util.Objects::nonNull).toList(),
                relations.stream().map(LearningVocabularyRelation::getRelatedTerm).toList());
        Map<String, List<VocabularyRelationResponse>> grouped = new LinkedHashMap<>();
        for (LearningVocabularyRelation relation : relations) {
            if (!isVisibleRelation(relation)) {
                continue;
            }
            List<VocabularyRelationResponse> values = grouped.computeIfAbsent(
                    relation.getNormalizedTerm(), ignored -> new ArrayList<>());
            if (values.size() < VocabularyInsightConstants.VISIBLE_RELATION_LIMIT) {
                values.add(toRelationResponse(relation, lookup));
            }
        }
        return grouped;
    }

    /** 批量补充关联词的英美音标。 */
    public List<VocabularyRelationResponse> enrichRelationPhonetics(List<VocabularyRelationResponse> relations) {
        return enrichRelationPhonetics(null, relations);
    }

    /** 批量补充关联词的英美音标。 */
    public List<VocabularyRelationResponse> enrichRelationPhonetics(Long vocabularyId, List<VocabularyRelationResponse> relations) {
        if (relations == null || relations.isEmpty()) {
            return relations;
        }
        relations = relations.stream()
                .filter(this::isVisibleRelation)
                .toList();
        RecordLookup lookup = loadRecords(
                java.util.stream.Stream.concat(java.util.stream.Stream.of(vocabularyId),
                                relations.stream().map(VocabularyRelationResponse::getRelatedVocabularyId))
                        .filter(java.util.Objects::nonNull).toList(),
                relations.stream().map(VocabularyRelationResponse::getRelatedTerm).toList());
        EnglishVocabularyStudyRecord sourceRecord = lookup.byId().get(vocabularyId);
        for (VocabularyRelationResponse relation : relations) {
            if (StringUtils.hasText(relation.getRelatedPhoneticUk()) || StringUtils.hasText(relation.getRelatedPhoneticUs())) {
                continue;
            }
            Phonetic phonetic = firstPhonetic(
                    extractRelationPhonetic(sourceRecord, relation.getRelationType(), relation.getRelatedTerm()),
                    extractPhonetic(lookup.related(relation.getRelatedVocabularyId(), relation.getRelatedTerm())));
            relation.setRelatedPhoneticUk(phonetic.uk());
            relation.setRelatedPhoneticUs(phonetic.us());
        }
        return relations;
    }

    private void collectPartOfSpeechTags(JsonNode root, EnglishVocabularyStudyRecord record,
                                         Map<String, LearningVocabularyTag> tags, LocalDateTime now) {
        JsonNode definitions = firstExisting(root, "definitions", "meanings", "translations", "definition");
        if (definitions == null) {
            return;
        }
        for (JsonNode item : iterable(definitions)) {
            String pos = firstText(item, VocabularyTagType.PART_OF_SPEECH.getCode(), "partOfSpeech", "pos", "type", "word_class");
            if (StringUtils.hasText(pos)) {
                addTag(tags, record, VocabularyTagType.PART_OF_SPEECH.getCode(), normalizeValue(pos), pos,
                        VocabularyInsightConstants.TAG_WEIGHT_PART_OF_SPEECH, now);
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
            addTag(tags, record, VocabularyTagType.MEANING_TOPIC.getCode(), topic, topic,
                    VocabularyInsightConstants.TAG_WEIGHT_MEANING_TOPIC, now);
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
                                                              LocalDateTime now,
                                                              Function<String, EnglishVocabularyStudyRecord> relatedRecordResolver) {
        Map<String, LearningVocabularyRelation> relations = new LinkedHashMap<>();
        collectArrayRelations(root, record, relations, now, "synonyms", VocabularyRelationType.SYNONYM,
                VocabularyInsightConstants.RELATION_SCORE_SYNONYM, relatedRecordResolver);
        collectArrayRelations(root, record, relations, now, "antonyms", VocabularyRelationType.ANTONYM,
                VocabularyInsightConstants.RELATION_SCORE_ANTONYM, relatedRecordResolver);
        collectArrayRelations(root, record, relations, now, "word_family", VocabularyRelationType.WORD_FAMILY,
                VocabularyInsightConstants.RELATION_SCORE_WORD_FAMILY, relatedRecordResolver);
        collectArrayRelations(root, record, relations, now, "wordFamily", VocabularyRelationType.WORD_FAMILY,
                VocabularyInsightConstants.RELATION_SCORE_WORD_FAMILY, relatedRecordResolver);

        return relations.values().stream().limit(VocabularyInsightConstants.MAX_RELATIONS).toList();
    }

    private void collectArrayRelations(JsonNode root, EnglishVocabularyStudyRecord record,
                                       Map<String, LearningVocabularyRelation> relations,
                                       LocalDateTime now, String field, VocabularyRelationType relationType, int score,
                                       Function<String, EnglishVocabularyStudyRecord> relatedRecordResolver) {
        JsonNode node = root.get(field);
        if (node == null) {
            return;
        }
        for (JsonNode item : iterable(node)) {
            String text = readableText(item);
            if (StringUtils.hasText(text)) {
                addRelation(relations, record, text, relationType, relationValue(item, relationType), score, now,
                        firstText(item, VocabularyTagType.PART_OF_SPEECH.getCode(), "partOfSpeech", "pos", "type", "word_class"),
                        firstText(item, "meaning", "meaning_cn", "meaningCn", "translation", "translation_cn", "cn", "definition"),
                        StringUtils.hasText(firstText(item, "word", "term", "phrase", VocabularyRelationType.COLLOCATION.getCode(), "text", "value", "name"))
                                ? VocabularyMatchType.PARSED_OBJECT.getCode() : VocabularyMatchType.PARSED_TEXT.getCode(),
                        StringUtils.hasText(firstText(item, "meaning", "meaning_cn", "meaningCn", "translation", "translation_cn", "cn", "definition")) ? score : null,
                        relatedRecordResolver);
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
        tag.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        tag.setVocabularyId(record.getId());
        tag.setNormalizedTerm(record.getNormalizedTerm());
        tag.setTagType(tagType);
        tag.setTagValue(limit(cleanValue, VocabularyInsightConstants.TAG_VALUE_MAX_LENGTH));
        tag.setDisplayName(limit(StringUtils.hasText(displayName) ? displayName.trim() : cleanValue,
                VocabularyInsightConstants.TAG_VALUE_MAX_LENGTH));
        tag.setWeight(weight);
        tag.setSource(VocabularyInsightConstants.SOURCE_PARSED_JSON);
        tag.setDeleted(false);
        tag.setVersion(CommonConstants.ZERO);
        tag.setCreateTime(now);
        tag.setUpdateTime(now);
        tags.putIfAbsent(key, tag);
    }

    private void addRelation(Map<String, LearningVocabularyRelation> relations, EnglishVocabularyStudyRecord record,
                             String relatedTerm, VocabularyRelationType relationType, String relationValue, int score, LocalDateTime now,
                             String parsedPartOfSpeech, String parsedMeaning, String parsedMatchType, Integer parsedMatchScore,
                             Function<String, EnglishVocabularyStudyRecord> relatedRecordResolver) {
        String normalizedRelated = normalizeTerm(cleanRelationText(relatedTerm));
        if (!StringUtils.hasText(normalizedRelated) || normalizedRelated.equals(record.getNormalizedTerm())) {
            return;
        }
        EnglishVocabularyStudyRecord relatedRecord = relatedRecordResolver.apply(normalizedRelated);
        CoreMeaning coreMeaning = extractCoreMeaning(relatedRecord);
        String key = relationType.getCode() + ":" + normalizedRelated;
        LearningVocabularyRelation relation = new LearningVocabularyRelation();
        relation.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        relation.setVocabularyId(record.getId());
        relation.setRelatedVocabularyId(relatedRecord == null ? null : relatedRecord.getId());
        relation.setNormalizedTerm(record.getNormalizedTerm());
        relation.setRelatedTerm(limit(normalizedRelated, VocabularyInsightConstants.TAG_VALUE_MAX_LENGTH));
        relation.setRelationType(relationType.getCode());
        relation.setRelationValue(limit(relationValue, VocabularyInsightConstants.TAG_VALUE_MAX_LENGTH));
        relation.setRelatedPartOfSpeech(limit(firstNonBlank(parsedPartOfSpeech, coreMeaning.partOfSpeech()),
                VocabularyInsightConstants.PART_OF_SPEECH_MAX_LENGTH));
        relation.setRelatedMeaning(limit(firstNonBlank(parsedMeaning, coreMeaning.meaning()),
                VocabularyInsightConstants.MEANING_MAX_LENGTH));
        relation.setMatchType(limit(resolveMatchType(parsedMatchType, relatedRecord, parsedMeaning),
                VocabularyInsightConstants.MATCH_TYPE_MAX_LENGTH));
        relation.setMatchScore(parsedMatchScore == null ? (relatedRecord == null ? null : VocabularyConstants.EXACT_MATCH_SCORE) : parsedMatchScore);
        relation.setScore(score);
        relation.setSource(VocabularyInsightConstants.SOURCE_PARSED_JSON);
        relation.setDeleted(false);
        relation.setVersion(CommonConstants.ZERO);
        relation.setCreateTime(now);
        relation.setUpdateTime(now);
        relations.putIfAbsent(key, relation);
    }

    private Set<String> extractRelatedTerms(JsonNode root) {
        Set<String> terms = new LinkedHashSet<>();
        for (VocabularyRelationType type : List.of(
                VocabularyRelationType.SYNONYM,
                VocabularyRelationType.ANTONYM,
                VocabularyRelationType.WORD_FAMILY)) {
            for (String field : type.getJsonFields()) {
                JsonNode node = root.get(field);
                if (node == null) {
                    continue;
                }
                for (JsonNode item : iterable(node)) {
                    String term = normalizeTerm(cleanRelationText(readableText(item)));
                    if (StringUtils.hasText(term)) {
                        terms.add(term);
                    }
                }
            }
        }
        return terms;
    }

    private <T> void insertChunks(List<T> values, Function<List<T>, Integer> writer) {
        for (int start = 0; start < values.size(); start += WRITE_BATCH_SIZE) {
            writer.apply(values.subList(start, Math.min(start + WRITE_BATCH_SIZE, values.size())));
        }
    }

    private RecordLookup loadRecords(Collection<Long> vocabularyIds, Collection<String> normalizedTerms) {
        Set<Long> ids = vocabularyIds == null ? Set.of() : vocabularyIds.stream()
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> terms = normalizedTerms == null ? Set.of() : normalizedTerms.stream()
                .filter(StringUtils::hasText).map(this::normalizeTerm)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty() && terms.isEmpty()) {
            return RecordLookup.empty();
        }
        LambdaQueryWrapper<EnglishVocabularyStudyRecord> wrapper = new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                .eq(EnglishVocabularyStudyRecord::getDeleted, false)
                .and(condition -> {
                    if (!ids.isEmpty()) {
                        condition.in(EnglishVocabularyStudyRecord::getId, ids);
                    }
                    if (!terms.isEmpty()) {
                        if (!ids.isEmpty()) {
                            condition.or();
                        }
                        condition.in(EnglishVocabularyStudyRecord::getNormalizedTerm, terms);
                    }
                });
        List<EnglishVocabularyStudyRecord> records = recordMapper.selectList(wrapper);
        return new RecordLookup(
                records.stream().collect(java.util.stream.Collectors.toMap(
                        EnglishVocabularyStudyRecord::getId, value -> value, (left, right) -> left)),
                records.stream().collect(java.util.stream.Collectors.toMap(
                        EnglishVocabularyStudyRecord::getNormalizedTerm, value -> value, (left, right) -> left)));
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
        if (definitionCount >= VocabularyInsightConstants.HARD_DEFINITION_COUNT || length >= VocabularyInsightConstants.HARD_WORD_LENGTH) {
            return VocabularyDifficulty.HARD.getCode();
        }
        if (definitionCount >= VocabularyInsightConstants.MEDIUM_DEFINITION_COUNT || length >= VocabularyInsightConstants.MEDIUM_WORD_LENGTH) {
            return VocabularyDifficulty.MEDIUM.getCode();
        }
        return VocabularyDifficulty.EASY.getCode();
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

    private String relationValue(JsonNode item, VocabularyRelationType relationType) {
        String value = firstText(item, "relation", "relation_value", "relationValue", "note", "reason");
        if (StringUtils.hasText(value)) {
            return value;
        }
        if (relationType == VocabularyRelationType.COLLOCATION) {
            return firstText(item, "meaning", "meaning_cn", "meaningCn", "translation", "translation_cn", "cn", "definition");
        }
        return "";
    }

    private boolean isVisibleRelation(VocabularyRelationResponse relation) {
        return relation != null && isVisibleRelationType(relation.getRelationType());
    }

    private boolean isVisibleRelation(LearningVocabularyRelation relation) {
        return relation != null && isVisibleRelationType(relation.getRelationType());
    }

    private boolean isVisibleRelationType(String relationType) {
        return VocabularyRelationType.of(relationType).isVisibleInRelatedWords();
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
            for (String key : List.of("word", "term", "phrase", VocabularyRelationType.COLLOCATION.getCode(), "text", "value", "name", "meaning")) {
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

    /** 从结构化词卡提取核心词性和含义。 */
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
                String partOfSpeech = firstText(item, VocabularyTagType.PART_OF_SPEECH.getCode(), "partOfSpeech", "pos", "type", "word_class");
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
        VocabularyMatchType parsedType = VocabularyMatchType.of(parsedMatchType);
        if (StringUtils.hasText(parsedMatchType) && parsedType != VocabularyMatchType.PARSED_TEXT) {
            return parsedType.getCode();
        }
        if (relatedRecord != null) {
            return VocabularyMatchType.CACHED_EXACT.getCode();
        }
        if (StringUtils.hasText(parsedMeaning)) {
            return VocabularyMatchType.PARSED_OBJECT.getCode();
        }
        return StringUtils.hasText(parsedMatchType) ? parsedType.getCode() : VocabularyMatchType.PARSED_TEXT.getCode();
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

    private VocabularyRelationResponse toRelationResponse(LearningVocabularyRelation relation, RecordLookup lookup) {
        VocabularyRelationResponse response = new VocabularyRelationResponse();
        response.setId(relation.getId());
        response.setRelatedVocabularyId(relation.getRelatedVocabularyId());
        response.setRelatedTerm(relation.getRelatedTerm());
        response.setRelationType(relation.getRelationType());
        response.setRelationValue(relation.getRelationValue());
        response.setRelatedPartOfSpeech(relation.getRelatedPartOfSpeech());
        response.setRelatedMeaning(relation.getRelatedMeaning());
        Phonetic phonetic = firstPhonetic(
                extractRelationPhonetic(lookup.byId().get(relation.getVocabularyId()),
                        relation.getRelationType(), relation.getRelatedTerm()),
                extractPhonetic(lookup.related(relation.getRelatedVocabularyId(), relation.getRelatedTerm())));
        response.setRelatedPhoneticUk(phonetic.uk());
        response.setRelatedPhoneticUs(phonetic.us());
        response.setMatchType(relation.getMatchType());
        response.setMatchScore(relation.getMatchScore());
        response.setScore(relation.getScore());
        return response;
    }

    private Phonetic extractRelationPhonetic(EnglishVocabularyStudyRecord sourceRecord, String relationType, String relatedTerm) {
        if (sourceRecord == null || !StringUtils.hasText(sourceRecord.getParsedJson()) || !StringUtils.hasText(relatedTerm)) {
            return Phonetic.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(sourceRecord.getParsedJson());
            String normalizedRelated = normalizeTerm(cleanRelationText(relatedTerm));
            for (String field : relationFields(relationType)) {
                JsonNode node = root.get(field);
                if (node == null) {
                    continue;
                }
                for (JsonNode item : iterable(node)) {
                    String candidate = normalizeTerm(cleanRelationText(readableText(item)));
                    if (normalizedRelated.equals(candidate)) {
                        return extractPhonetic(item);
                    }
                }
            }
        } catch (Exception ignored) {
            return Phonetic.empty();
        }
        return Phonetic.empty();
    }

    private List<String> relationFields(String relationType) {
        return VocabularyRelationType.of(relationType).getJsonFields();
    }

    private Phonetic firstPhonetic(Phonetic preferred, Phonetic fallback) {
        if (preferred != null && (StringUtils.hasText(preferred.uk()) || StringUtils.hasText(preferred.us()))) {
            return preferred;
        }
        return fallback == null ? Phonetic.empty() : fallback;
    }

    private Phonetic extractPhonetic(JsonNode root) {
        if (root == null || root.isNull()) {
            return Phonetic.empty();
        }
        JsonNode phonetic = firstExisting(root, "phonetic", "phonetics", "pronunciation");
        String uk = firstText(phonetic, "uk", "uk_phonetic", "ukPhonetic", "british", "br");
        String us = firstText(phonetic, "us", "us_phonetic", "usPhonetic", "american", "am");
        if (!StringUtils.hasText(uk)) {
            uk = firstText(root, "phonetic_uk", "phoneticUk", "uk_phonetic", "ukPhonetic", "uk");
        }
        if (!StringUtils.hasText(us)) {
            us = firstText(root, "phonetic_us", "phoneticUs", "us_phonetic", "usPhonetic", "us");
        }
        return new Phonetic(uk, us);
    }

    /** 从词卡结构中提取英美音标。 */
    public Phonetic extractPhonetic(EnglishVocabularyStudyRecord record) {
        if (record == null || !StringUtils.hasText(record.getParsedJson())) {
            return Phonetic.empty();
        }
        try {
            return extractPhonetic(objectMapper.readTree(record.getParsedJson()));
        } catch (Exception ignored) {
            return Phonetic.empty();
        }
    }

    private List<LearningVocabularyAlias> collectAliases(
            JsonNode root, EnglishVocabularyStudyRecord record, LocalDateTime now) {
        Map<String, LearningVocabularyAlias> aliases = new LinkedHashMap<>();
        String rawTerm = record.getTerm();
        String normalizedTerm = record.getNormalizedTerm();
        String lemma = root.path("lemma").asText(rawTerm);
        if (!StringUtils.hasText(lemma)) {
            lemma = rawTerm;
        }
        String normalizedLemma = normalizeTerm(lemma);

        // 1. 本身精确词
        addAlias(aliases, record, rawTerm, normalizedTerm, lemma, normalizedLemma, "exact", "system", now);

        // 2. 原型词（若不同）
        if (StringUtils.hasText(lemma) && !normalizedLemma.equals(normalizedTerm)) {
            addAlias(aliases, record, lemma, normalizedLemma, lemma, normalizedLemma, "lemma", "ai", now);
        }

        // 3. AI 输出的 inflections / word_forms 列表
        JsonNode inflectionsNode = root.path("inflections");
        if (inflectionsNode.isArray()) {
            for (JsonNode item : inflectionsNode) {
                String alias = item.asText();
                if (StringUtils.hasText(alias)) {
                    addAlias(aliases, record, alias, normalizeTerm(alias), lemma, normalizedLemma, "inflection", "ai", now);
                }
            }
        }
        JsonNode wordFormsNode = root.path("word_forms");
        if (wordFormsNode.isArray()) {
            for (JsonNode item : wordFormsNode) {
                String alias = item.isObject() ? item.path("word").asText(item.path("term").asText()) : item.asText();
                if (StringUtils.hasText(alias)) {
                    addAlias(aliases, record, alias, normalizeTerm(alias), lemma, normalizedLemma, "word_form", "ai", now);
                }
            }
        }

        // 4. 词形还原器推荐的原型候选（建立双向索引）
        List<String> candidateLemmas = lemmatizer.candidateLemmas(normalizedTerm);
        for (String candidate : candidateLemmas) {
            addAlias(aliases, record, candidate, candidate, lemma, normalizedLemma, "rule_lemma", "rule", now);
        }

        return new ArrayList<>(aliases.values());
    }

    private void addAlias(Map<String, LearningVocabularyAlias> aliases, EnglishVocabularyStudyRecord record,
                          String aliasTerm, String normalizedAlias, String lemma, String normalizedLemma,
                          String aliasType, String source, LocalDateTime now) {
        if (!StringUtils.hasText(normalizedAlias)) {
            return;
        }
        if (aliases.containsKey(normalizedAlias)) {
            return;
        }
        LearningVocabularyAlias alias = new LearningVocabularyAlias();
        alias.setId(IdWorker.getId());
        alias.setCreateBy(0L);
        alias.setUpdateBy(0L);
        alias.setVocabularyId(record.getId());
        alias.setAliasTerm(aliasTerm.trim());
        alias.setNormalizedAlias(normalizedAlias);
        alias.setLemma(lemma.trim());
        alias.setNormalizedLemma(normalizedLemma);
        alias.setAliasType(aliasType);
        alias.setSource(source);
        alias.setCreateTime(now);
        alias.setUpdateTime(now);
        alias.setDeleted(false);
        alias.setVersion(CommonConstants.ZERO);
        aliases.put(normalizedAlias, alias);
    }

    /**
 * 当前业务领域组件。
 */
    /** 表示词汇的核心词性和含义。 */
    public record CoreMeaning(String partOfSpeech, String meaning) {
        static CoreMeaning empty() {
            return new CoreMeaning("", "");
        }
    }

    /**
 * 当前业务领域组件。
 */
    /** 表示词汇的英美音标。 */
    public record Phonetic(String uk, String us) {
        static Phonetic empty() {
            return new Phonetic("", "");
        }
    }

    private record InsightInput(EnglishVocabularyStudyRecord record, JsonNode root) {
    }

    private record RecordLookup(Map<Long, EnglishVocabularyStudyRecord> byId,
                                Map<String, EnglishVocabularyStudyRecord> byTerm) {
        private static RecordLookup empty() {
            return new RecordLookup(Map.of(), Map.of());
        }

        private EnglishVocabularyStudyRecord related(Long id, String term) {
            EnglishVocabularyStudyRecord record = id == null ? null : byId.get(id);
            if (record != null || term == null) {
                return record;
            }
            return byTerm.get(term.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
        }
    }
}
