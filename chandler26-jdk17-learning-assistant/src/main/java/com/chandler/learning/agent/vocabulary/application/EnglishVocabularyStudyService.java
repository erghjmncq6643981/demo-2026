package com.chandler.learning.agent.vocabulary.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.ai.chat.application.AgentChatRequest;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyBestMatchResponse;
import com.chandler.learning.agent.vocabulary.api.response.VocabularySuggestionResponse;
import com.chandler.learning.agent.vocabulary.api.request.VocabularyStudyRequest;
import com.chandler.learning.agent.vocabulary.api.response.VocabularyStudyResponse;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningVocabularyAlias;
import com.chandler.learning.agent.vocabulary.domain.entity.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.learning.domain.enums.LearningScene;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.vocabulary.domain.enums.VocabularyMatchType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.LearningVocabularyAliasMapper;
import com.chandler.learning.agent.vocabulary.infrastructure.mapper.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.vocabulary.application.VocabularyInsightService;
import com.chandler.learning.agent.ai.agent.domain.constant.AiScenarioConstants;
import com.chandler.learning.agent.ai.chat.domain.constant.AiChatConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 英语词汇学习服务。
 * <p>
 * 优先读取本地结构化缓存（支持形态变体别名与词形还原）；缓存缺失或强制刷新时再调用 AI，避免重复消耗模型额度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnglishVocabularyStudyService {

    private final EnglishVocabularyStudyRecordMapper recordMapper;
    private final LearningVocabularyAliasMapper aliasMapper;
    private final EnglishLemmatizer lemmatizer;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;
    private final VocabularyInsightService vocabularyInsightService;
    private final VocabularyAudioService vocabularyAudioService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    /**
     * 多级查找词汇缓存：
     * 1. 规范化精确匹配
     * 2. 形态别名表匹配
     * 3. 词形还原器推导的原型匹配
     */
    public EnglishVocabularyStudyRecord findRecord(String normalizedTerm) {
        if (!StringUtils.hasText(normalizedTerm)) {
            return null;
        }
        // 1. 精确主表查找
        EnglishVocabularyStudyRecord record = findByNormalizedTerm(normalizedTerm);
        if (record != null) {
            return record;
        }
        // 2. 形态别名表查找（具备容错兜底）
        try {
            LearningVocabularyAlias alias = aliasMapper.findByNormalizedAlias(normalizedTerm);
            if (alias != null && alias.getVocabularyId() != null) {
                EnglishVocabularyStudyRecord aliasRecord = recordMapper.selectById(alias.getVocabularyId());
                if (aliasRecord != null && !Boolean.TRUE.equals(aliasRecord.getDeleted())) {
                    return aliasRecord;
                }
            }
        } catch (Exception ex) {
            log.debug("别名索引查询跳过: {}", ex.getMessage());
        }
        // 3. 词形还原推导原型查找
        List<String> candidates = lemmatizer.candidateLemmas(normalizedTerm);
        for (String candidate : candidates) {
            EnglishVocabularyStudyRecord candidateRecord = findByNormalizedTerm(candidate);
            if (candidateRecord != null) {
                try {
                    vocabularyInsightService.syncInsights(candidateRecord);
                } catch (Exception ignored) {
                }
                return candidateRecord;
            }
            try {
                LearningVocabularyAlias candidateAlias = aliasMapper.findByNormalizedAlias(candidate);
                if (candidateAlias != null && candidateAlias.getVocabularyId() != null) {
                    EnglishVocabularyStudyRecord matched = recordMapper.selectById(candidateAlias.getVocabularyId());
                    if (matched != null && !Boolean.TRUE.equals(matched.getDeleted())) {
                        vocabularyInsightService.syncInsights(matched);
                        return matched;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private final Map<String, CompletableFuture<VocabularyStudyResponse>> inFlightLookups = new ConcurrentHashMap<>();

    /** 生成或读取学习材料。 */
    public VocabularyStudyResponse study(VocabularyStudyRequest request) {
        String rawTerm = request.getTerm() == null ? "" : request.getTerm().trim();
        String normalizedTerm = normalize(rawTerm);
        if (!StringUtils.hasText(normalizedTerm)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.VOCABULARY_EMPTY,
                    "单词不能为空");
        }

        boolean forceRefresh = Boolean.TRUE.equals(request.getForceRefresh());
        EnglishVocabularyStudyRecord existing = forceRefresh ? null : findRecord(normalizedTerm);
        if (existing != null) {
            touch(existing);
            try {
                vocabularyInsightService.syncInsights(existing);
            } catch (Exception ignored) {
            }
            systemLogService.record(null, SystemLogType.CACHE, "读取词汇缓存", normalizedTerm);
            log.debug("词汇缓存命中 term={} recordId={} lookupCount={} queriedTerm={}",
                    normalizedTerm,
                    existing.getId(),
                    existing.getLookupCount(),
                    rawTerm);
            vocabularyAudioService.prefetchAudio(existing.getTerm());
            return toResponse(existing, true, rawTerm);
        }

        if (!forceRefresh) {
            CompletableFuture<VocabularyStudyResponse> runningFuture = inFlightLookups.get(normalizedTerm);
            if (runningFuture != null) {
                log.info("检测到词汇「{}」正在进行 AI 查词，合并复用当前任务", normalizedTerm);
                try {
                    return runningFuture.join();
                } catch (Exception ex) {
                    log.warn("等待并发查词任务失败，尝试重新发起生成: {}", ex.getMessage());
                }
            }
        }

        CompletableFuture<VocabularyStudyResponse> future = new CompletableFuture<>();
        CompletableFuture<VocabularyStudyResponse> existingFuture = inFlightLookups.putIfAbsent(normalizedTerm, future);
        if (existingFuture != null && !forceRefresh) {
            log.info("检测到词汇「{}」已被并发发起 AI 查词，合并复用结果", normalizedTerm);
            return existingFuture.join();
        }

        try {
            VocabularyStudyResponse response = doGenerate(request, normalizedTerm, rawTerm, forceRefresh);
            future.complete(response);
            return response;
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
            if (throwable instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(throwable);
        } finally {
            inFlightLookups.remove(normalizedTerm, future);
        }
    }

    private VocabularyStudyResponse doGenerate(VocabularyStudyRequest request, String normalizedTerm, String rawTerm, boolean forceRefresh) {
        // 若输入为复数/时态（如 slogans），确定目标原形词（如 slogan）
        List<String> candidateLemmas = lemmatizer.candidateLemmas(normalizedTerm);
        String targetGenerationTerm = candidateLemmas.isEmpty() ? normalizedTerm : candidateLemmas.get(0);

        log.debug("开始生成词汇学习卡片 term={} targetLemma={} forceRefresh={} modelConfigId={}",
                normalizedTerm,
                targetGenerationTerm,
                forceRefresh,
                request.getModelConfigId());
        // 传递标准原形词给 AI 进行词卡生成，确保释义、音标与卡片主体均为标准原形
        AgentChatResponse chatResponse = aiChat(request, targetGenerationTerm);
        String parsedJson = normalizeCardPayload(
                chatResponse.requireStructuredRoot(AiInvocationScene.VOCABULARY_CARD_SINGLE), targetGenerationTerm);
        validateCardPayload(parsedJson);

        // 统一提取标准原形（Lemma）作为主词卡的主键和标准展示词，严禁将复数/时态存为独立主词卡
        String canonicalDisplayTerm = targetGenerationTerm;
        String canonicalTerm = targetGenerationTerm;
        try {
            JsonNode root = objectMapper.readTree(parsedJson);
            String parsedLemma = root.path("lemma").asText();
            if (!StringUtils.hasText(parsedLemma)) {
                parsedLemma = root.path("term").asText(targetGenerationTerm);
            }
            if (StringUtils.hasText(parsedLemma)) {
                canonicalDisplayTerm = parsedLemma.trim();
                canonicalTerm = normalize(canonicalDisplayTerm);
            }
        } catch (Exception ignored) {
        }

        EnglishVocabularyStudyRecord record = findByNormalizedTerm(canonicalTerm);
        if (record == null && !canonicalTerm.equals(normalizedTerm)) {
            record = findByNormalizedTerm(normalizedTerm);
        }
        if (record == null) {
            record = new EnglishVocabularyStudyRecord();
        }
        record.setTerm(canonicalDisplayTerm);
        record.setNormalizedTerm(canonicalTerm);
        record.setAgentCode(resolveAgentCode(request));
        record.setTemplateCode(resolveTemplateCode(request));
        record.setProvider(chatResponse.getModelProvider());
        record.setModelName(chatResponse.getModelName());
        record.setSessionId(chatResponse.getSessionId());
        record.setRawContent(chatResponse.getContent());
        record.setParsedJson(parsedJson);
        record.setTokenUsage(chatResponse.getTokenUsage());
        record.setCostTime(chatResponse.getCostTime());
        record.setLookupCount(record.getLookupCount() == null
                ? VocabularyConstants.DEFAULT_LOOKUP_COUNT
                : record.getLookupCount() + VocabularyConstants.DEFAULT_LOOKUP_COUNT);
        record.setLastLookupTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());

        if (record.getId() == null) {
            record.setCreateTime(LocalDateTime.now());
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }

        // 若存在旧的历史复数脏记录（例如主键为 slogans），清理该脏记录以防污染模糊匹配
        if (!canonicalTerm.equals(normalizedTerm)) {
            EnglishVocabularyStudyRecord dirtyPluralRecord = findByNormalizedTerm(normalizedTerm);
            if (dirtyPluralRecord != null && !dirtyPluralRecord.getId().equals(record.getId())) {
                recordMapper.deleteById(dirtyPluralRecord.getId());
            }
        }

        try {
            vocabularyInsightService.syncInsights(record);
        } catch (Exception ex) {
            log.warn("同步词汇洞察及别名异常: {}", ex.getMessage());
        }
        systemLogService.record(null, SystemLogType.AI, "AI 生成词汇卡片", canonicalTerm);
        log.info("用户「{}」使用「{} / {}」生成了单词「{}」的学习卡片，是否刷新缓存：{}",
                userDisplayNameService.currentUserName(),
                record.getProvider(),
                record.getModelName(),
                canonicalTerm,
                forceRefresh);
        try {
            vocabularyAudioService.prefetchAudio(canonicalTerm);
        } catch (Exception ex) {
            log.debug("异步预热音频异常 term={}: {}", canonicalTerm, ex.getMessage());
        }
        return toResponse(record, false, rawTerm);
    }

    /** 查询详情词汇。 */
    public VocabularyStudyResponse detail(String term) {
        String rawTerm = term == null ? "" : term.trim();
        String normalizedTerm = normalize(rawTerm);
        EnglishVocabularyStudyRecord record = findRecord(normalizedTerm);
        log.debug("查询词汇学习缓存 term={} found={}", normalizedTerm, record != null);
        if (record != null) {
            return toResponse(record, true, rawTerm);
        }
        if (inFlightLookups.containsKey(normalizedTerm)) {
            VocabularyStudyResponse generatingResponse = new VocabularyStudyResponse();
            generatingResponse.setTerm(rawTerm);
            generatingResponse.setNormalizedTerm(normalizedTerm);
            generatingResponse.setCacheHit(false);
            generatingResponse.setGenerating(true);
            return generatingResponse;
        }
        return null;
    }

    /** 查询拼写最相近的词汇。 */
    public VocabularyBestMatchResponse bestMatch(String term) {
        String rawTerm = term == null ? "" : term.trim();
        String normalizedTerm = normalize(rawTerm);
        if (!StringUtils.hasText(normalizedTerm)) {
            return null;
        }
        EnglishVocabularyStudyRecord exact = findRecord(normalizedTerm);
        if (exact != null) {
            log.debug("词汇最匹配查询命中精确结果 query={} recordId={}", normalizedTerm, exact.getId());
            return toBestMatchResponse(rawTerm, exact, VocabularyConstants.EXACT_MATCH_SCORE,
                    VocabularyMatchType.EXACT.getCode());
        }

        List<EnglishVocabularyStudyRecord> candidates = recordMapper.selectList(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                .orderByDesc(EnglishVocabularyStudyRecord::getUpdateTime)
                .last("LIMIT " + VocabularyConstants.FUZZY_MATCH_CANDIDATE_LIMIT));
        VocabularyBestMatchResponse response = candidates.stream()
                .map(candidate -> new MatchCandidate(candidate, matchScore(normalizedTerm, candidate.getNormalizedTerm())))
                .filter(candidate -> candidate.score() >= VocabularyConstants.FUZZY_MATCH_MIN_SCORE)
                .max(Comparator.comparingInt(MatchCandidate::score)
                        .thenComparing(candidate -> candidate.record().getLookupCount() == null
                                ? CommonConstants.ZERO
                                : candidate.record().getLookupCount()))
                .map(candidate -> toBestMatchResponse(normalizedTerm, candidate.record(), candidate.score(),
                        VocabularyMatchType.FUZZY.getCode()))
                .orElse(null);
        log.debug("词汇最匹配查询使用模糊匹配 query={} found={} candidates={}",
                normalizedTerm,
                response != null,
                candidates.size());
        return response;
    }

    /**
     * 根据输入关键词前缀查询联想补全建议列表。
     */
    public List<VocabularySuggestionResponse> suggestions(String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (!StringUtils.hasText(normalizedKeyword)) {
            return List.of();
        }
        List<EnglishVocabularyStudyRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                        .likeRight(EnglishVocabularyStudyRecord::getNormalizedTerm, normalizedKeyword)
                        .orderByDesc(EnglishVocabularyStudyRecord::getLookupCount)
                        .last("LIMIT 8"));
        return records.stream().map(record -> {
            VocabularyInsightService.CoreMeaning coreMeaning = vocabularyInsightService.extractCoreMeaning(record);
            return new VocabularySuggestionResponse(
                    record.getTerm(),
                    record.getNormalizedTerm(),
                    coreMeaning.partOfSpeech(),
                    coreMeaning.meaning(),
                    record.getLookupCount());
        }).toList();
    }

    /**
     * 将词汇学习请求转换成 Agent 对话请求，模型输出仍由学习卡片解析逻辑统一处理。
     */
    private AgentChatResponse aiChat(VocabularyStudyRequest request, String normalizedTerm) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("term", normalizedTerm);

        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setInvocationScene(AiInvocationScene.VOCABULARY_CARD_SINGLE);
        chatRequest.setAgentCode(resolveAgentCode(request));
        chatRequest.setTemplateCode(resolveTemplateCode(request));
        chatRequest.setModelConfigId(request.getModelConfigId());
        chatRequest.setTitle("Vocabulary - " + normalizedTerm);
        chatRequest.setBusinessType(AiChatConstants.BUSINESS_TYPE_LEARNING);
        chatRequest.setBusinessId(LearningScene.ENGLISH_VOCABULARY.getCode());
        chatRequest.setMessage("请生成英语词汇「" + normalizedTerm + "」的结构化学习卡片。");
        chatRequest.setVariables(variables);
        return aiChatService.chat(chatRequest);
    }

    private EnglishVocabularyStudyRecord findByNormalizedTerm(String normalizedTerm) {
        if (!StringUtils.hasText(normalizedTerm)) {
            return null;
        }
        return recordMapper.selectOne(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                .eq(EnglishVocabularyStudyRecord::getNormalizedTerm, normalizedTerm)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    private void touch(EnglishVocabularyStudyRecord record) {
        record.setLookupCount(record.getLookupCount() == null
                ? VocabularyConstants.DEFAULT_LOOKUP_COUNT
                : record.getLookupCount() + VocabularyConstants.DEFAULT_LOOKUP_COUNT);
        record.setLastLookupTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
    }

    private VocabularyStudyResponse toResponse(EnglishVocabularyStudyRecord record, boolean cacheHit) {
        return toResponse(record, cacheHit, null);
    }

    private VocabularyStudyResponse toResponse(EnglishVocabularyStudyRecord record, boolean cacheHit, String queriedTerm) {
        VocabularyStudyResponse response = new VocabularyStudyResponse();
        response.setId(record.getId());
        String effectiveQueriedTerm = StringUtils.hasText(queriedTerm) ? queriedTerm : record.getTerm();
        response.setQueriedTerm(effectiveQueriedTerm);
        response.setTerm(record.getTerm());
        response.setNormalizedTerm(record.getNormalizedTerm());
        response.setCacheHit(cacheHit);

        String lemma = record.getTerm();
        List<String> inflections = new ArrayList<>();
        if (StringUtils.hasText(record.getParsedJson())) {
            try {
                JsonNode root = objectMapper.readTree(record.getParsedJson());
                String parsedLemma = root.path("lemma").asText();
                if (StringUtils.hasText(parsedLemma)) {
                    lemma = parsedLemma.trim();
                }
                JsonNode inflNode = root.path("inflections");
                if (inflNode.isArray()) {
                    for (JsonNode n : inflNode) {
                        String item = n.asText();
                        if (StringUtils.hasText(item)) {
                            inflections.add(item.trim());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        response.setLemma(lemma);
        response.setInflections(inflections);
        String normQueried = normalize(effectiveQueriedTerm);
        response.setIsAliasHit(StringUtils.hasText(normQueried) && !normQueried.equals(record.getNormalizedTerm()));

        response.setAgentCode(record.getAgentCode());
        response.setTemplateCode(record.getTemplateCode());
        response.setProvider(record.getProvider());
        response.setModelName(record.getModelName());
        response.setSessionId(record.getSessionId());
        response.setRawContent(record.getRawContent());
        response.setParsed(readParsed(record));
        response.setTokenUsage(record.getTokenUsage());
        response.setCostTime(record.getCostTime());
        response.setLookupCount(record.getLookupCount());
        response.setTags(vocabularyInsightService.listTags(record.getId()));
        response.setRelations(vocabularyInsightService.listRelations(record.getNormalizedTerm()));
        return response;
    }

    private VocabularyBestMatchResponse toBestMatchResponse(String query, EnglishVocabularyStudyRecord record,
                                                            int matchScore, String matchType) {
        VocabularyInsightService.CoreMeaning coreMeaning = vocabularyInsightService.extractCoreMeaning(record);
        VocabularyBestMatchResponse response = new VocabularyBestMatchResponse();
        response.setQuery(query);
        response.setMatchedTerm(record.getTerm());
        response.setNormalizedTerm(record.getNormalizedTerm());
        response.setPartOfSpeech(coreMeaning.partOfSpeech());
        response.setMeaning(coreMeaning.meaning());
        response.setMatchScore(matchScore);
        response.setMatchType(matchType);
        response.setRecord(toResponse(record, true, query));
        return response;
    }

    private Object readParsed(EnglishVocabularyStudyRecord record) {
        if (!StringUtils.hasText(record.getParsedJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(record.getParsedJson(), Object.class);
        } catch (Exception ex) {
            log.warn("词汇结构化 JSON 读取失败 recordId={} term={} error={}",
                    record.getId(),
                    record.getNormalizedTerm(),
                    ex.getMessage());
            return null;
        }
    }

    /**
     * 兼容模型返回纯 JSON、Markdown 代码块或包裹对象等多种格式，自动修复常见结构包装与字段别名，最终落库为标准 JSON 字符串。
     */
    private String normalizeCardPayload(JsonNode root, String fallbackTerm) {
        if (root == null || !root.isObject()) {
            return null;
        }
        try {
            if (root instanceof ObjectNode objectNode) {
                if (!StringUtils.hasText(objectNode.path("term").asText()) && StringUtils.hasText(fallbackTerm)) {
                    objectNode.put("term", fallbackTerm);
                }
                normalizeScalarField(objectNode, "lemma", List.of("base_form", "baseForm", "root_word", "rootWord", "prototype"));
                normalizePhoneticField(objectNode);
                normalizeArrayField(objectNode, "inflections", List.of("inflection", "word_forms", "wordForms", "forms", "conjugations"));
                normalizeArrayField(objectNode, "definitions", List.of("meaning", "meanings", "definition"));
                normalizeArrayField(objectNode, "examples", List.of("example_sentences", "example", "sentences", "exampleSentences"));
                normalizeArrayField(objectNode, "collocations", List.of("phrases", "collocation", "common_phrases", "commonPhrases"));
                normalizeScalarField(objectNode, "memory_tips", List.of("memoryTips", "tips", "memory_tip", "mnemonic", "memory"));
                normalizeArrayField(objectNode, "related_words", List.of("relatedWords", "relations", "related"));
                normalizeRelatedWordsPhonetics(objectNode);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                LearningErrorCode.JSON_SERIALIZE_FAILED,
                    "词卡结构化响应标准化失败",
                    ex);
        }
    }

    private void normalizeRelatedWordsPhonetics(ObjectNode objectNode) {
        for (String field : List.of("synonyms", "antonyms", "word_family", "wordFamily")) {
            JsonNode array = objectNode.get(field);
            if (array != null && array.isArray()) {
                for (JsonNode item : array) {
                    if (item instanceof ObjectNode itemObj) {
                        normalizePhoneticField(itemObj);
                    }
                }
            }
        }
    }

    private void normalizePhoneticField(ObjectNode objectNode) {
        JsonNode phoneticNode = objectNode.path("phonetic");
        String uk = null;
        String us = null;
        if (phoneticNode.isObject()) {
            uk = phoneticNode.path("uk").asText(null);
            us = phoneticNode.path("us").asText(null);
            if (uk == null) uk = phoneticNode.path("en").asText(null);
            if (us == null) us = phoneticNode.path("am").asText(null);
        } else if (phoneticNode.isTextual() && StringUtils.hasText(phoneticNode.asText())) {
            uk = phoneticNode.asText();
            us = phoneticNode.asText();
        }
        if (!StringUtils.hasText(uk)) {
            uk = firstNonBlankText(objectNode, "phonetic.uk", "phonetic_uk", "uk_phonetic", "phoneticUk", "ukPhonetic", "uk");
        }
        if (!StringUtils.hasText(us)) {
            us = firstNonBlankText(objectNode, "phonetic.us", "phonetic_us", "us_phonetic", "phoneticUs", "usPhonetic", "us", "am");
        }
        if (StringUtils.hasText(uk) || StringUtils.hasText(us)) {
            ObjectNode pNode = objectNode.putObject("phonetic");
            if (StringUtils.hasText(uk)) {
                pNode.put("uk", formatPhonetic(uk));
            }
            if (StringUtils.hasText(us)) {
                pNode.put("us", formatPhonetic(us));
            }
        }
    }

    private String firstNonBlankText(ObjectNode objectNode, String... fields) {
        for (String field : fields) {
            JsonNode node = objectNode.get(field);
            if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
                return node.asText().trim();
            }
        }
        return null;
    }

    private String formatPhonetic(String text) {
        if (!StringUtils.hasText(text)) return "";
        String clean = text.trim().replaceFirst("(?i)^UK\\s*", "").replaceFirst("(?i)^US\\s*", "");
        if (!clean.startsWith("/")) clean = "/" + clean;
        if (!clean.endsWith("/")) clean = clean + "/";
        return clean;
    }

    private void normalizeArrayField(ObjectNode objectNode, String targetField, List<String> aliases) {
        JsonNode value = resolveFieldValue(objectNode, targetField, aliases);
        if (value == null) {
            objectNode.putArray(targetField);
        } else if (!value.isArray()) {
            ArrayNode arrayNode = objectNode.putArray(targetField);
            arrayNode.add(value);
        }
    }

    private void normalizeScalarField(ObjectNode objectNode, String targetField, List<String> aliases) {
        resolveFieldValue(objectNode, targetField, aliases);
    }

    private JsonNode resolveFieldValue(ObjectNode objectNode, String targetField, List<String> aliases) {
        JsonNode value = objectNode.get(targetField);
        if (value != null && !value.isNull()) {
            return value;
        }
        for (String alias : aliases) {
            value = objectNode.get(alias);
            if (value != null && !value.isNull()) {
                objectNode.set(targetField, value);
                return value;
            }
        }
        return null;
    }

    /** 词卡只有通过最小结构校验后才能写入共享缓存，避免坏响应污染后续学习。 */
    private void validateCardPayload(String parsedJson) {
        if (!StringUtils.hasText(parsedJson)) {
            throw LearningAssistantException.badRequest(LearningErrorCode.AI_RESPONSE_PARSE_FAILED, "AI 返回内容不是有效 JSON");
        }
        try {
            JsonNode root = objectMapper.readTree(parsedJson);
            if (!root.isObject() || !StringUtils.hasText(root.path("term").asText())) {
                throw LearningAssistantException.badRequest(LearningErrorCode.AI_RESPONSE_PARSE_FAILED, "AI 返回内容缺少单词 term 字段");
            }
            if (!root.path("definitions").isArray() || root.path("definitions").isEmpty()) {
                throw LearningAssistantException.badRequest(LearningErrorCode.AI_RESPONSE_PARSE_FAILED, "AI 返回内容缺少 definitions 释义列表");
            }
        } catch (LearningAssistantException ex) {
            throw ex;
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningErrorCode.JSON_PARSE_FAILED,
                    "词汇学习卡片解析失败",
                    ex);
        }
    }

    private String normalize(String term) {
        return term == null ? "" : term.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private int matchScore(String query, String candidate) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(candidate)) {
            return VocabularyConstants.MIN_MATCH_SCORE;
        }
        if (query.equals(candidate)) {
            return VocabularyConstants.EXACT_MATCH_SCORE;
        }
        int distance = levenshtein(query, candidate);
        int maxLength = Math.max(query.length(), candidate.length());
        int score = Math.max(VocabularyConstants.MIN_MATCH_SCORE,
                (int) Math.round((1D - (double) distance / maxLength) * VocabularyConstants.EXACT_MATCH_SCORE));
        if (candidate.startsWith(query) || query.startsWith(candidate)) {
            score += VocabularyConstants.PREFIX_SCORE_BOOST;
        }
        if (query.charAt(0) == candidate.charAt(0)) {
            score += VocabularyConstants.SAME_INITIAL_SCORE_BOOST;
        }
        if (commonPrefixLength(query, candidate) >= VocabularyConstants.COMMON_PREFIX_MIN_LENGTH) {
            score += VocabularyConstants.COMMON_PREFIX_SCORE_BOOST;
        }
        if (candidate.contains(query) || query.contains(candidate)) {
            score += VocabularyConstants.CONTAINS_SCORE_BOOST;
        }
        return Math.min(score, VocabularyConstants.FUZZY_MATCH_MAX_SCORE);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + VocabularyConstants.EDIT_DISTANCE_INSERT_DELETE_COST];
        int[] current = new int[right.length() + VocabularyConstants.EDIT_DISTANCE_INSERT_DELETE_COST];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[CommonConstants.ZERO] = row;
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - VocabularyConstants.EDIT_DISTANCE_INSERT_DELETE_COST)
                        == right.charAt(column - VocabularyConstants.EDIT_DISTANCE_INSERT_DELETE_COST)
                        ? VocabularyConstants.EDIT_DISTANCE_SAME_COST
                        : VocabularyConstants.EDIT_DISTANCE_REPLACE_COST;
                current[column] = Math.min(Math.min(
                        current[column - VocabularyConstants.EDIT_DISTANCE_INSERT_DELETE_COST] + VocabularyConstants.EDIT_DISTANCE_INSERT_DELETE_COST,
                        previous[column] + VocabularyConstants.EDIT_DISTANCE_INSERT_DELETE_COST),
                        previous[column - VocabularyConstants.EDIT_DISTANCE_INSERT_DELETE_COST] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private int commonPrefixLength(String left, String right) {
        int length = Math.min(left.length(), right.length());
        int index = CommonConstants.ZERO;
        while (index < length && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    private String resolveAgentCode(VocabularyStudyRequest request) {
        return StringUtils.hasText(request.getAgentCode()) ? request.getAgentCode() : AiScenarioConstants.VOCABULARY_AGENT_CODE;
    }

    private String resolveTemplateCode(VocabularyStudyRequest request) {
        return StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : AiScenarioConstants.VOCABULARY_TEMPLATE_CODE;
    }

    /**
 * 当前业务领域组件。
 */
    private record MatchCandidate(EnglishVocabularyStudyRecord record, int score) {
    }
}
