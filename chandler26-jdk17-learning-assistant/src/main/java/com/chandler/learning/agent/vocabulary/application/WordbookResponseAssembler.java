package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyRelationResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyTagResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntryResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookEntrySummaryResponse;
import com.chandler.learning.agent.vocabulary.api.response.WordbookResponse;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.vocabulary.domain.entity.VocabularyCatalogEntry;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookEntryMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningWordbookMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单词本响应装配与词卡快照边界。
 * <p>
 * 词本服务只负责业务命令和状态变更，完整词卡、标签、关联词的读取和个人快照冻结由本组件统一处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WordbookResponseAssembler {

    private final LearningWordbookMapper wordbookMapper;
    private final LearningWordbookEntryMapper entryMapper;
    private final EnglishVocabularyStudyRecordMapper vocabularyMapper;
    private final VocabularyInsightService vocabularyInsightService;
    private final ObjectMapper objectMapper;

    /** 将个人单词本领域对象转换为接口响应。 */
    public WordbookResponse toWordbookResponse(LearningWordbook wordbook) {
        WordbookResponse response = new WordbookResponse();
        response.setId(wordbook.getId());
        response.setName(wordbook.getName());
        response.setDescription(wordbook.getDescription());
        response.setIsDefault(wordbook.getIsDefault());
        response.setEntryCount(entryMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getWordbookId, wordbook.getId())
                .eq(LearningWordbookEntry::getDeleted, false)));
        response.setDueCount(entryMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getWordbookId, wordbook.getId())
                .eq(LearningWordbookEntry::getDeleted, false)
                .le(LearningWordbookEntry::getNextReviewTime, LocalDateTime.now())));
        response.setCreateTime(wordbook.getCreateTime());
        return response;
    }

    /** 将个人单词本领域对象转换为接口响应。 */
    public WordbookEntryResponse toEntryResponse(LearningWordbookEntry entry) {
        WordbookEntryResponse response = new WordbookEntryResponse();
        response.setId(entry.getId());
        response.setWordbookId(entry.getWordbookId());
        response.setVocabularyId(entry.getVocabularyId());
        response.setProgressId(entry.getProgressId());
        response.setCatalogEntryId(entry.getCatalogEntryId());
        response.setTerm(entry.getTerm());
        response.setNormalizedTerm(entry.getNormalizedTerm());
        response.setNote(entry.getNote());
        response.setStatus(StringUtils.hasText(entry.getStatus()) ? entry.getStatus() : inferStatus(entry));
        response.setReviewStage(entry.getReviewStage());
        response.setMasteryScore(entry.getMasteryScore());
        response.setLastReviewTime(entry.getLastReviewTime());
        response.setNextReviewTime(entry.getNextReviewTime());
        response.setReviewCount(entry.getReviewCount());
        response.setCorrectCount(entry.getCorrectCount());
        response.setWrongCount(entry.getWrongCount());
        response.setCreateTime(entry.getCreateTime());
        response.setParsed(readEntryParsed(entry));
        response.setSnapshotProvider(entry.getSnapshotProvider());
        response.setSnapshotModelName(entry.getSnapshotModelName());
        response.setSnapshotSessionId(entry.getSnapshotSessionId());
        response.setSnapshotTime(entry.getSnapshotTime());
        response.setCardStatus(entry.getCardStatus());
        response.setCardErrorMessage(entry.getCardErrorMessage());
        response.setCardGeneratedTime(entry.getCardGeneratedTime());
        response.setTags(readEntryTags(entry));
        response.setRelations(readEntryRelations(entry));
        return response;
    }

    /** 将个人单词本领域对象转换为接口响应。 */
    public WordbookEntrySummaryResponse toSummaryResponse(LearningWordbookEntry entry) {
        WordbookEntrySummaryResponse response = new WordbookEntrySummaryResponse();
        response.setId(entry.getId());
        response.setWordbookId(entry.getWordbookId());
        response.setTerm(entry.getTerm());
        response.setNormalizedTerm(entry.getNormalizedTerm());
        response.setStatus(entry.getStatus());
        response.setReviewStage(entry.getReviewStage());
        response.setMasteryScore(entry.getMasteryScore());
        response.setLastReviewTime(entry.getLastReviewTime());
        response.setNextReviewTime(entry.getNextReviewTime());
        response.setReviewCount(entry.getReviewCount());
        response.setCorrectCount(entry.getCorrectCount());
        response.setWrongCount(entry.getWrongCount());
        response.setCardStatus(entry.getCardStatus());
        response.setCreateTime(entry.getCreateTime());
        populateSummaryCardInfo(response, entry);
        return response;
    }

    private void populateSummaryCardInfo(WordbookEntrySummaryResponse response, LearningWordbookEntry entry) {
        String json = entry.getSnapshotParsedJson();
        if (!StringUtils.hasText(json) && entry.getVocabularyId() != null) {
            EnglishVocabularyStudyRecord record = vocabularyMapper.selectById(entry.getVocabularyId());
            if (record != null) {
                json = record.getParsedJson();
            }
        }
        if (!StringUtils.hasText(json)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return;
            }
            JsonNode phoneticNode = root.get("phonetic");
            String phonetic = null;
            if (phoneticNode != null && phoneticNode.isObject()) {
                phonetic = firstText(phoneticNode, "uk", "us", "uk_phonetic", "us_phonetic");
            } else if (phoneticNode != null && phoneticNode.isTextual()) {
                phonetic = phoneticNode.asText();
            }
            if (!StringUtils.hasText(phonetic)) {
                phonetic = firstText(root, "phonetic_uk", "phoneticUk", "phonetic_us", "phoneticUs");
            }
            response.setPhonetic(StringUtils.hasText(phonetic) ? phonetic : null);

            JsonNode defsNode = root.get("definitions");
            if (defsNode != null && defsNode.isArray() && !defsNode.isEmpty()) {
                List<String> defTexts = new ArrayList<>();
                for (JsonNode def : defsNode) {
                    String pos = firstText(def, "pos", "partOfSpeech");
                    String meaning = firstText(def, "meaning", "cn", "en", "explanation", "definition");
                    if (StringUtils.hasText(meaning)) {
                        if (StringUtils.hasText(pos) && !"pos".equalsIgnoreCase(pos) && !"meaning".equalsIgnoreCase(pos)) {
                            String normalizedPos = pos.endsWith(".") ? pos : pos + ".";
                            defTexts.add(meaning.startsWith(normalizedPos) ? meaning : normalizedPos + " " + meaning);
                        } else {
                            defTexts.add(meaning);
                        }
                    }
                }
                if (!defTexts.isEmpty()) {
                    response.setMeaningText(String.join("； ", defTexts));
                }
            } else {
                String meaning = firstText(root, "meaning", "definition", "translation", "explanation");
                if (StringUtils.hasText(meaning)) {
                    response.setMeaningText(meaning);
                }
            }
        } catch (Exception ex) {
            log.debug("解析词条摘要 JSON 失败 entryId={} error={}", entry.getId(), ex.getMessage());
        }
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String name : fieldNames) {
            JsonNode child = node.get(name);
            if (child != null && child.isTextual() && StringUtils.hasText(child.asText())) {
                return child.asText().trim();
            }
        }
        return null;
    }

    /** 应用个人单词本状态变更。 */
    public void applyVocabularySnapshot(LearningWordbookEntry entry, EnglishVocabularyStudyRecord vocabulary,
                                        LocalDateTime now) {
        String tagsJson = writeJson(vocabularyInsightService.listTags(vocabulary.getId()),
                "单词本词条标签快照序列化失败");
        String relationsJson = writeJson(vocabularyInsightService.listRelations(vocabulary.getNormalizedTerm()),
                "单词本词条关联词快照序列化失败");
        applyVocabularySnapshot(entry, vocabulary, now, tagsJson, relationsJson);
    }

    /** 应用个人单词本状态变更。 */
    public void applyVocabularySnapshot(LearningWordbookEntry entry, EnglishVocabularyStudyRecord vocabulary,
                                        LocalDateTime now, String tagsJson, String relationsJson) {
        entry.applyVocabularySnapshot(vocabulary, now, tagsJson, relationsJson);
    }

    /** 公共词卡更新时刷新个人快照并保留学习状态。 */
    public boolean refreshSnapshotIfVocabularyChanged(LearningWordbookEntry entry,
                                                      EnglishVocabularyStudyRecord vocabulary,
                                                      LocalDateTime now) {
        if (vocabulary == null) {
            return false;
        }
        boolean snapshotMissing = !StringUtils.hasText(entry.getSnapshotParsedJson());
        boolean sessionChanged = vocabulary.getSessionId() != null
                && !vocabulary.getSessionId().equals(entry.getSnapshotSessionId());
        boolean vocabularyNewer = vocabulary.getUpdateTime() != null
                && (entry.getSnapshotTime() == null || vocabulary.getUpdateTime().isAfter(entry.getSnapshotTime()));
        if (!snapshotMissing && !sessionChanged && !vocabularyNewer) {
            return false;
        }
        applyVocabularySnapshot(entry, vocabulary, now);
        entry.refreshVocabularyIdentity(vocabulary, now);
        return true;
    }

    /** 根据公共词表词条构建基础词卡快照。 */
    public String basicSnapshot(VocabularyCatalogEntry source, String term) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("meaning", source == null ? null : source.getDefinitionText());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("term", term);
        snapshot.put("phonetic", source == null ? null : source.getPhonetic());
        snapshot.put("definitions", List.of(definition));
        snapshot.put("importedBasicCard", true);
        return writeJson(snapshot, "场景词条基础快照序列化失败");
    }

    private String readEntryParsed(LearningWordbookEntry entry) {
        if (StringUtils.hasText(entry.getSnapshotParsedJson())) {
            return entry.getSnapshotParsedJson();
        }
        if (entry.getVocabularyId() == null) {
            return null;
        }
        EnglishVocabularyStudyRecord record = vocabularyMapper.selectById(entry.getVocabularyId());
        return record == null ? null : record.getParsedJson();
    }

    private List<VocabularyTagResponse> readEntryTags(LearningWordbookEntry entry) {
        if (StringUtils.hasText(entry.getSnapshotTagsJson())) {
            List<VocabularyTagResponse> tags = readJsonList(entry.getSnapshotTagsJson(), VocabularyTagResponse.class,
                    "单词本词条标签快照读取失败", entry);
            if (tags != null) {
                return tags;
            }
        }
        if (entry.getVocabularyId() == null) {
            return List.of();
        }
        return vocabularyInsightService.listTags(entry.getVocabularyId());
    }

    private List<VocabularyRelationResponse> readEntryRelations(LearningWordbookEntry entry) {
        if (StringUtils.hasText(entry.getSnapshotRelationsJson())) {
            List<VocabularyRelationResponse> relations = readJsonList(entry.getSnapshotRelationsJson(), VocabularyRelationResponse.class,
                    "单词本词条关联词快照读取失败", entry);
            if (relations != null) {
                return vocabularyInsightService.enrichRelationPhonetics(entry.getVocabularyId(), relations);
            }
        }
        if (entry.getVocabularyId() == null) {
            return List.of();
        }
        return vocabularyInsightService.listRelations(entry.getNormalizedTerm());
    }

    private <T> List<T> readJsonList(String json, Class<T> elementType, String errorMessage,
                                     LearningWordbookEntry entry) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, elementType));
        } catch (Exception ex) {
            log.warn("{} entryId={} term={} error={}", errorMessage, entry.getId(),
                    entry.getNormalizedTerm(), ex.getMessage());
            return null;
        }
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("{} error={}", errorMessage, ex.getMessage());
            return null;
        }
    }

    private String inferStatus(LearningWordbookEntry entry) {
        return com.chandler.learning.agent.learning.domain.enums.ReviewStatus
                .infer(entry.getMasteryScore(), entry.getWrongCount(), entry.getCorrectCount()).getCode();
    }
}
