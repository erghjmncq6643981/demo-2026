package com.chandler.learning.agent.reading.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.ai.chat.application.AgentChatRequest;
import com.chandler.learning.agent.ai.chat.application.AgentChatResponse;
import com.chandler.learning.agent.reading.api.request.ArticleStudyAnswerRequest;
import com.chandler.learning.agent.reading.api.request.ArticleStudyCompleteRequest;
import com.chandler.learning.agent.reading.api.request.ArticleStudyProgressRequest;
import com.chandler.learning.agent.reading.api.request.ArticleStudyRequest;
import com.chandler.learning.agent.reading.api.response.ArticleStudyResponse;
import com.chandler.learning.agent.reading.api.response.ArticleStudyPageResponse;
import com.chandler.learning.agent.reading.api.response.ArticleStudySummaryResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.learning.agent.reading.api.response.ArticleStudyWordResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningArticleStudyRecord;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbook;
import com.chandler.learning.agent.vocabulary.domain.entity.LearningWordbookEntry;
import com.chandler.learning.agent.reading.domain.enums.ArticleDifficulty;
import com.chandler.learning.agent.reading.domain.enums.ArticleWordCountRange;
import com.chandler.learning.agent.ai.chat.domain.enums.AiInvocationScene;
import com.chandler.learning.agent.learning.domain.enums.LearningScene;
import com.chandler.learning.agent.learning.domain.enums.ReviewStatus;
import com.chandler.learning.agent.system.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.identity.application.UserDisplayNameService;
import com.chandler.learning.agent.reading.infrastructure.mapper.LearningArticleStudyRecordMapper;
import com.chandler.learning.agent.vocabulary.application.WordbookService;
import com.chandler.learning.agent.ai.chat.application.AiChatService;
import com.chandler.learning.agent.ai.agent.domain.constant.AiScenarioConstants;
import com.chandler.learning.agent.ai.chat.domain.constant.AiChatConstants;
import com.chandler.learning.agent.common.constant.CommonConstants;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.reading.domain.constant.ArticleConstants;
import com.chandler.learning.agent.system.application.SystemLogService;
import com.chandler.learning.agent.vocabulary.application.LearningWordProgressService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 语境精读服务。
 * <p>
 * 根据用户从单词本中选择的词汇生成英语学习文章，并保存 AI 原始回复和结构化解析结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleStudyService {

    private final LearningArticleStudyRecordMapper articleStudyRecordMapper;
    private final WordbookService wordbookService;
    private final AiChatService aiChatService;
    private final LearningWordProgressService progressService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;
    private final ObjectMapper objectMapper;

    /**
     * 处理 {@code study} 相关业务。
     */
    public ArticleStudyResponse study(Long userId, ArticleStudyRequest request) {
        LearningWordbook wordbook = requireWordbook(userId, request.getWordbookId());
        ArticleWordCountRange wordCountRange = ArticleWordCountRange.of(request.getWordCountRange());
        ArticleDifficulty difficulty = ArticleDifficulty.of(request.getDifficulty());
        String remark = trimToNull(request.getRemark());
        List<Long> entryIds = normalizeEntryIds(request.getEntryIds());
        List<LearningWordbookEntry> entries = requireEntries(userId, wordbook.getId(), entryIds);
        List<ArticleStudyWordResponse> selectedWords = entries.stream().map(this::toSelectedWord).toList();
        String selectedTermsJson = writeJson(selectedWords, "语境精读词汇摘要序列化失败");
        String selectedTermHash = hash(userId, wordbook.getId(), selectedWords, wordCountRange, difficulty, remark);

        boolean forceRefresh = Boolean.TRUE.equals(request.getForceRefresh());
        LearningArticleStudyRecord existing = forceRefresh ? null : findCached(userId, wordbook.getId(), selectedTermHash);
        if (existing != null) {
            existing.touch(LocalDateTime.now());
            articleStudyRecordMapper.updateById(existing);
            systemLogService.record(userId, SystemLogType.CACHE, "读取语境精读缓存",
                    wordbook.getName() + "，" + selectedWords.size() + " 个单词");
            log.info("用户「{}」打开了单词本「{}」中 {} 个目标词的语境精读缓存",
                    userDisplayNameService.userName(userId),
                    wordbook.getName(),
                    selectedWords.size());
            log.debug("语境精读缓存命中 userId={} wordbookId={} recordId={} hash={}",
                    userId, wordbook.getId(), existing.getId(), selectedTermHash);
            return toResponse(existing, true);
        }

        log.debug("开始生成语境精读材料 userId={} wordbookId={} wordCountRange={} difficulty={} forceRefresh={} words={}",
                userId,
                wordbook.getId(),
                wordCountRange.getCode(),
                difficulty.getCode(),
                forceRefresh,
                selectedWords.stream().map(ArticleStudyWordResponse::getNormalizedTerm).toList());
        AgentChatResponse chatResponse = aiChat(userId, request, selectedWords, wordCountRange, difficulty, remark);
        LocalDateTime now = LocalDateTime.now();
        LearningArticleStudyRecord record = LearningArticleStudyRecord.create(
                userId,
                wordbook.getId(),
                selectedTermsJson,
                selectedTermHash,
                wordCountRange.getCode(),
                difficulty.getCode(),
                remark,
                resolveAgentCode(request),
                resolveTemplateCode(request),
                now);
        record.applyAiResult(
                chatResponse.getModelProvider(),
                chatResponse.getModelName(),
                chatResponse.getSessionId(),
                chatResponse.getContent(),
                normalizeArticlePayload(
                        chatResponse.requireStructuredRoot(AiInvocationScene.ARTICLE_STUDY_MATERIAL), selectedWords),
                chatResponse.getTokenUsage(),
                chatResponse.getCostTime(),
                now);
        articleStudyRecordMapper.insert(record);

        systemLogService.record(userId, SystemLogType.AI, "生成语境精读材料",
                wordbook.getName() + "，" + selectedWords.size() + " 个单词，" + wordCountRange.getLabel() + "，" + difficulty.getLabel());
        log.info("用户「{}」基于单词本「{}」中的 {} 个目标词生成了「{}」「{}」难度的语境精读材料",
                userDisplayNameService.userName(userId),
                wordbook.getName(),
                selectedWords.size(),
                wordCountRange.getLabel(),
                difficulty.getLabel());
        log.debug("语境精读材料已保存 userId={} wordbookId={} recordId={} sessionId={} provider={} model={}",
                userId,
                wordbook.getId(),
                record.getId(),
                record.getSessionId(),
                record.getProvider(),
                record.getModelName());
        return toResponse(record, false);
    }

    /**
     * 查询 {@code listRecords} 相关业务。
     */
    public ArticleStudyPageResponse listRecords(Long userId, Long wordbookId, Integer page, Integer pageSize) {
        Long resolvedWordbookId = wordbookId == null ? null : requireWordbook(userId, wordbookId).getId();
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        Page<LearningArticleStudyRecord> result = new Page<>(current, size);
        articleStudyRecordMapper.selectPage(result, new LambdaQueryWrapper<LearningArticleStudyRecord>()
                        .select(LearningArticleStudyRecord::getId,
                                LearningArticleStudyRecord::getWordbookId,
                                LearningArticleStudyRecord::getSelectedTermsJson,
                                LearningArticleStudyRecord::getWordCountRange,
                                LearningArticleStudyRecord::getDifficulty,
                                LearningArticleStudyRecord::getStudyStatus,
                                LearningArticleStudyRecord::getCurrentStage,
                                LearningArticleStudyRecord::getPracticeTotal,
                                LearningArticleStudyRecord::getPracticeCorrect,
                                LearningArticleStudyRecord::getPracticeScore,
                                LearningArticleStudyRecord::getCreateTime,
                                LearningArticleStudyRecord::getUpdateTime)
                        .eq(LearningArticleStudyRecord::getUserId, userId)
                        .eq(resolvedWordbookId != null, LearningArticleStudyRecord::getWordbookId, resolvedWordbookId)
                        .eq(LearningArticleStudyRecord::getDeleted, false)
                        .orderByDesc(LearningArticleStudyRecord::getUpdateTime));
        ArticleStudyPageResponse response = new ArticleStudyPageResponse();
        response.setItems(result.getRecords().stream().map(this::toSummaryResponse).toList());
        response.setTotal(result.getTotal());
        response.setPage(current);
        response.setPageSize(size);
        return response;
    }

    /**
     * 查询 {@code detail} 相关业务。
     */
    public ArticleStudyResponse detail(Long userId, Long recordId) {
        LearningArticleStudyRecord record = requireRecord(userId, recordId);
        record.touch(LocalDateTime.now());
        articleStudyRecordMapper.updateById(record);
        log.debug("语境精读记录详情已读取 userId={} recordId={}", userId, recordId);
        return toResponse(record, true);
    }

    /**
     * 开始语境精读或保存当前学习阶段。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArticleStudyResponse updateProgress(Long userId, Long recordId, ArticleStudyProgressRequest request) {
        LearningArticleStudyRecord record = requireRecord(userId, recordId);
        String stage = normalizeStage(request.getStage());
        boolean firstStart = !ArticleConstants.STATUS_IN_PROGRESS.equals(record.getStudyStatus())
                && !ArticleConstants.STATUS_COMPLETED.equals(record.getStudyStatus());
        record.moveToStage(stage, LocalDateTime.now());
        articleStudyRecordMapper.updateById(record);
        if (firstStart) {
            systemLogService.record(userId, SystemLogType.REVIEW, "开始语境精读",
                    articleTitle(record) + "，" + readSelectedWords(record).size() + " 个目标词");
            log.info("用户「{}」开始语境精读「{}」，目标词 {} 个",
                    userDisplayNameService.userName(userId), articleTitle(record), readSelectedWords(record).size());
        }
        return toResponse(record, true);
    }

    /**
     * 提交阅读检测并完成本次语境精读。
     */
    @Transactional(rollbackFor = Exception.class)
    public ArticleStudyResponse complete(Long userId, Long recordId, ArticleStudyCompleteRequest request) {
        LearningArticleStudyRecord record = requireRecord(userId, recordId);
        if (ArticleConstants.STATUS_COMPLETED.equals(record.getStudyStatus())) {
            return toResponse(record, true);
        }
        PracticeScore result = scorePractice(readParsedNode(record),
                request.getAnswers() == null ? List.of() : request.getAnswers());
        if (result.answered() < result.total()) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_PRACTICE_INCOMPLETE,
                    "请完成全部阅读检测后再结束本次精读");
        }
        LocalDateTime now = LocalDateTime.now();
        record.completeStudy(result.total(), result.correct(), result.score(), now);
        articleStudyRecordMapper.updateById(record);
        progressService.recordArticleExposures(userId, readSelectedWords(record).stream()
                .map(ArticleStudyWordResponse::getTerm)
                .filter(StringUtils::hasText)
                .toList());
        systemLogService.record(userId, SystemLogType.REVIEW, "完成语境精读",
                articleTitle(record) + "，检测 " + result.correct() + "/" + result.total());
        log.info("用户「{}」完成语境精读「{}」，阅读检测得分 {}，目标词 {} 个",
                userDisplayNameService.userName(userId), articleTitle(record), result.score(), readSelectedWords(record).size());
        return toResponse(record, false);
    }

    /**
     * 处理 {@code aiChat} 相关业务。
     */
    private AgentChatResponse aiChat(Long userId, ArticleStudyRequest request, List<ArticleStudyWordResponse> selectedWords,
                                     ArticleWordCountRange wordCountRange, ArticleDifficulty difficulty, String remark) {
        Map<String, Object> variables = new HashMap<>();
        List<Map<String, Object>> promptWords = selectedWords.stream().map(word -> {
            Map<String, Object> promptWord = new LinkedHashMap<>();
            promptWord.put("term", word.getTerm());
            promptWord.put("part_of_speech", word.getPartOfSpeech());
            promptWord.put("meaning", word.getMeaning());
            return promptWord;
        }).toList();
        variables.put("words", promptWords);
        variables.put("word_count_range", wordCountRange.getLabel());
        variables.put("difficulty", difficulty.getLabel());
        variables.put("difficulty_prompt", difficulty.getPrompt());
        variables.put("remark", StrUtil.blankToDefault(remark, "无特别备注"));

        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setUserId(userId);
        chatRequest.setInvocationScene(AiInvocationScene.ARTICLE_STUDY_MATERIAL);
        chatRequest.setAgentCode(resolveAgentCode(request));
        chatRequest.setTemplateCode(resolveTemplateCode(request));
        chatRequest.setModelConfigId(request.getModelConfigId());
        chatRequest.setTitle(LearningScene.ENGLISH_ARTICLE.getTitle());
        chatRequest.setBusinessType(AiChatConstants.BUSINESS_TYPE_LEARNING);
        chatRequest.setBusinessId(LearningScene.ENGLISH_ARTICLE.getCode());
        chatRequest.setSceneCode(LearningScene.ENGLISH_ARTICLE.getCode());
        chatRequest.setMessage("请基于用户选择的目标词生成英语语境精读材料。");
        chatRequest.setVariables(variables);
        return aiChatService.chat(chatRequest);
    }

    /**
     * 查询 {@code findCached} 相关业务。
     */
    private LearningArticleStudyRecord findCached(Long userId, Long wordbookId, String selectedTermHash) {
        return articleStudyRecordMapper.selectOne(new LambdaQueryWrapper<LearningArticleStudyRecord>()
                .eq(LearningArticleStudyRecord::getUserId, userId)
                .eq(LearningArticleStudyRecord::getWordbookId, wordbookId)
                .eq(LearningArticleStudyRecord::getSelectedTermHash, selectedTermHash)
                .eq(LearningArticleStudyRecord::getDeleted, false)
                .orderByDesc(LearningArticleStudyRecord::getUpdateTime)
                .last(CommonConstants.SQL_LIMIT_ONE));
    }

    /**
     * 处理 {@code normalizeEntryIds} 相关业务。
     */
    private List<Long> normalizeEntryIds(List<Long> entryIds) {
        if (CollUtil.isEmpty(entryIds)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_EMPTY,
                    "请选择要生成文章的单词");
        }
        List<Long> normalized = entryIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalized.size() < ArticleConstants.MIN_SELECTED_WORDS) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_EMPTY,
                    "请选择要生成文章的单词");
        }
        if (normalized.size() > ArticleConstants.MAX_SELECTED_WORDS) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORD_LIMIT_EXCEEDED,
                    "一次最多选择 " + ArticleConstants.MAX_SELECTED_WORDS + " 个单词生成文章");
        }
        return normalized;
    }

    /**
     * 处理 {@code requireEntries} 相关业务。
     */
    private List<LearningWordbookEntry> requireEntries(Long userId, Long wordbookId, List<Long> entryIds) {
        List<LearningWordbookEntry> entries = wordbookService.findOwnedEntries(userId, wordbookId, entryIds);
        Map<Long, LearningWordbookEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(LearningWordbookEntry::getId, entry -> entry, (left, right) -> left, LinkedHashMap::new));
        List<LearningWordbookEntry> ordered = entryIds.stream()
                .map(entryMap::get)
                .filter(Objects::nonNull)
                .toList();
        if (ordered.size() != entryIds.size()) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_WORDS_INVALID,
                    "存在不属于当前单词本的词汇，请刷新后重新选择");
        }
        return ordered;
    }

    /**
     * 转换 {@code toSelectedWord} 相关业务。
     */
    private ArticleStudyWordResponse toSelectedWord(LearningWordbookEntry entry) {
        CoreMeaning coreMeaning = extractCoreMeaning(entry);
        ArticleStudyWordResponse response = new ArticleStudyWordResponse();
        response.setEntryId(entry.getId());
        response.setTerm(entry.getTerm());
        response.setNormalizedTerm(entry.getNormalizedTerm());
        response.setStatus(ReviewStatus.of(entry.getStatus()).getCode());
        response.setPartOfSpeech(coreMeaning.partOfSpeech());
        response.setMeaning(coreMeaning.meaning());
        return response;
    }

    /**
     * 处理 {@code extractCoreMeaning} 相关业务。
     */
    private CoreMeaning extractCoreMeaning(LearningWordbookEntry entry) {
        if (!StringUtils.hasText(entry.getSnapshotParsedJson())) {
            return new CoreMeaning(null, null);
        }
        try {
            JsonNode parsed = objectMapper.readTree(entry.getSnapshotParsedJson());
            JsonNode definition = firstDefinition(parsed);
            String partOfSpeech = text(definition, "part_of_speech", "partOfSpeech", "pos", "type", "word_class");
            String meaning = text(definition, "meaning", "meaning_cn", "meaningCn", "translation", "translation_cn", "cn", "chinese");
            if (!StringUtils.hasText(meaning)) {
                meaning = text(parsed, "meaning", "translation", "cn", "chinese");
            }
            return new CoreMeaning(partOfSpeech, meaning);
        } catch (Exception ex) {
            log.debug("语境精读词汇核心含义读取失败 entryId={} term={} error={}",
                    entry.getId(),
                    entry.getNormalizedTerm(),
                    ex.getMessage());
            return new CoreMeaning(null, null);
        }
    }

    /**
     * 处理 {@code firstDefinition} 相关业务。
     */
    private JsonNode firstDefinition(JsonNode parsed) {
        JsonNode definitions = parsed == null ? null : parsed.path("definitions");
        if (definitions != null && definitions.isArray() && definitions.size() > 0) {
            return definitions.get(0);
        }
        JsonNode meanings = parsed == null ? null : parsed.path("meanings");
        if (meanings != null && meanings.isArray() && meanings.size() > 0) {
            return meanings.get(0);
        }
        return parsed;
    }

    /**
     * 处理 {@code text} 相关业务。
     */
    private String text(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    /**
     * 判断 {@code hash} 相关业务。
     */
    private String hash(Long userId, Long wordbookId, List<ArticleStudyWordResponse> selectedWords,
                        ArticleWordCountRange wordCountRange, ArticleDifficulty difficulty, String remark) {
        String terms = selectedWords.stream()
                .map(ArticleStudyWordResponse::getNormalizedTerm)
                .filter(StringUtils::hasText)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(","));
        return DigestUtil.sha256Hex(userId + "|" + wordbookId + "|" + terms + "|"
                + wordCountRange.getCode() + "|" + difficulty.getCode() + "|" + StrUtil.blankToDefault(remark, ""));
    }

    /**
     * 转换 {@code toResponse} 相关业务。
     */
    private ArticleStudyResponse toResponse(LearningArticleStudyRecord record, boolean cacheHit) {
        ArticleStudyResponse response = new ArticleStudyResponse();
        response.setId(record.getId());
        response.setWordbookId(record.getWordbookId());
        response.setSelectedWords(readSelectedWords(record));
        response.setWordCountRange(record.getWordCountRange());
        response.setDifficulty(record.getDifficulty());
        response.setRemark(record.getRemark());
        response.setCacheHit(cacheHit);
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
        response.setStudyStatus(StringUtils.hasText(record.getStudyStatus())
                ? record.getStudyStatus() : ArticleConstants.STATUS_GENERATED);
        response.setCurrentStage(StringUtils.hasText(record.getCurrentStage())
                ? record.getCurrentStage() : ArticleConstants.STAGE_READING);
        response.setPracticeTotal(record.getPracticeTotal());
        response.setPracticeCorrect(record.getPracticeCorrect());
        response.setPracticeScore(record.getPracticeScore());
        response.setStartedTime(record.getStartedTime());
        response.setCompletedTime(record.getCompletedTime());
        response.setCreateTime(record.getCreateTime());
        response.setUpdateTime(record.getUpdateTime());
        return response;
    }

    private ArticleStudySummaryResponse toSummaryResponse(LearningArticleStudyRecord record) {
        ArticleStudySummaryResponse response = new ArticleStudySummaryResponse();
        response.setId(record.getId());
        response.setWordbookId(record.getWordbookId());
        response.setSelectedWords(readSelectedWords(record));
        response.setWordCountRange(record.getWordCountRange());
        response.setDifficulty(record.getDifficulty());
        response.setStudyStatus(record.getStudyStatus());
        response.setCurrentStage(record.getCurrentStage());
        response.setPracticeTotal(record.getPracticeTotal());
        response.setPracticeCorrect(record.getPracticeCorrect());
        response.setPracticeScore(record.getPracticeScore());
        response.setCreateTime(record.getCreateTime());
        response.setUpdateTime(record.getUpdateTime());
        return response;
    }

    /**
     * 查询 {@code readSelectedWords} 相关业务。
     */
    private List<ArticleStudyWordResponse> readSelectedWords(LearningArticleStudyRecord record) {
        if (!StringUtils.hasText(record.getSelectedTermsJson())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(record.getSelectedTermsJson(), new TypeReference<List<ArticleStudyWordResponse>>() {
            });
        } catch (Exception ex) {
            log.debug("语境精读词汇摘要读取失败 recordId={} error={}", record.getId(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * 查询 {@code readParsed} 相关业务。
     */
    private Object readParsed(LearningArticleStudyRecord record) {
        if (!StringUtils.hasText(record.getParsedJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(record.getParsedJson(), Object.class);
        } catch (Exception ex) {
            log.debug("语境精读结构化 JSON 读取失败 recordId={} error={}", record.getId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 处理 {@code extractJson} 相关业务。
     */
    private String normalizeArticlePayload(JsonNode parsed, List<ArticleStudyWordResponse> selectedWords) {
        if (parsed == null || !parsed.isObject()) {
            throw articleInvalid("AI 返回的语境精读材料不是有效 JSON，请重新生成");
        }
        validateArticlePayload(parsed, selectedWords);
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningErrorCode.JSON_SERIALIZE_FAILED,
                    "语境精读材料序列化失败",
                    ex);
        }
    }

    private void validateArticlePayload(JsonNode parsed, List<ArticleStudyWordResponse> selectedWords) {
        String article = requiredText(parsed, "article", "text", "content");
        requiredText(parsed, "title");
        requiredText(parsed, "translation", "translation_cn", "translationCn", "cn", "zh");
        String normalizedArticle = normalizeAnswer(article);
        JsonNode vocabulary = parsed.path("vocabulary_focus");
        if (!vocabulary.isArray()) {
            vocabulary = parsed.path("vocabularyFocus");
        }
        if (!vocabulary.isArray()) {
            throw articleInvalid("AI 返回的精读材料缺少目标词讲解");
        }
        Set<String> focusedWords = new java.util.HashSet<>();
        for (JsonNode item : vocabulary) {
            String term = text(item, "word", "term");
            if (StringUtils.hasText(term)) {
                focusedWords.add(normalizeAnswer(term));
            }
        }
        for (ArticleStudyWordResponse selectedWord : selectedWords) {
            String term = normalizeAnswer(selectedWord.getTerm());
            if (!normalizedArticle.contains(term)) {
                throw articleInvalid("AI 生成的文章没有覆盖目标词: " + selectedWord.getTerm());
            }
            if (!focusedWords.contains(term)) {
                throw articleInvalid("AI 生成的词汇精讲缺少目标词: " + selectedWord.getTerm());
            }
        }
        JsonNode practice = parsed.path("practice");
        if (!practice.isArray() || practice.size() != ArticleConstants.PRACTICE_QUESTION_COUNT) {
            throw articleInvalid("阅读检测必须包含 " + ArticleConstants.PRACTICE_QUESTION_COUNT + " 道题");
        }
        for (JsonNode question : practice) {
            requiredText(question, "question", "stem");
            JsonNode options = question.path("options");
            if (!options.isArray() || options.size() != 4) {
                throw articleInvalid("每道阅读检测题必须包含 4 个选项");
            }
            String correctAnswer = requiredText(question, "correct_answer", "correctAnswer", "answer");
            boolean answerInOptions = false;
            for (JsonNode option : options) {
                if (normalizeAnswer(option.asText()).equals(normalizeAnswer(correctAnswer))) {
                    answerInOptions = true;
                    break;
                }
            }
            if (!answerInOptions) {
                throw articleInvalid("阅读检测题的正确答案必须包含在选项中");
            }
        }
    }

    static PracticeScore scorePractice(JsonNode parsed, List<ArticleStudyAnswerRequest> answers) {
        JsonNode practice = parsed == null ? null : parsed.path("practice");
        if (practice == null || !practice.isArray()) {
            return new PracticeScore(CommonConstants.ZERO, CommonConstants.ZERO,
                    CommonConstants.ZERO, CommonConstants.ZERO);
        }
        Map<Integer, String> submitted = answers.stream()
                .filter(Objects::nonNull)
                .filter(answer -> answer.getQuestionIndex() != null && StringUtils.hasText(answer.getAnswer()))
                .collect(Collectors.toMap(ArticleStudyAnswerRequest::getQuestionIndex,
                        ArticleStudyAnswerRequest::getAnswer, (left, right) -> right, LinkedHashMap::new));
        int answered = CommonConstants.ZERO;
        int correct = CommonConstants.ZERO;
        for (int index = CommonConstants.ZERO; index < practice.size(); index++) {
            String answer = submitted.get(index);
            if (!StringUtils.hasText(answer)) {
                continue;
            }
            answered++;
            String expected = textValue(practice.get(index), "correct_answer", "correctAnswer", "answer");
            if (normalizeAnswer(answer).equals(normalizeAnswer(expected))) {
                correct++;
            }
        }
        int total = practice.size();
        int score = total == CommonConstants.ZERO
                ? CommonConstants.ZERO
                : (int) Math.round(correct * 100.0 / total);
        return new PracticeScore(total, answered, correct, score);
    }

    private LearningArticleStudyRecord requireRecord(Long userId, Long recordId) {
        LearningArticleStudyRecord record = articleStudyRecordMapper.selectOne(
                new LambdaQueryWrapper<LearningArticleStudyRecord>()
                        .eq(LearningArticleStudyRecord::getId, recordId)
                        .eq(LearningArticleStudyRecord::getUserId, userId)
                        .eq(LearningArticleStudyRecord::getDeleted, false)
                        .last(CommonConstants.SQL_LIMIT_ONE));
        if (record == null) {
            throw LearningAssistantException.notFound(
                    LearningErrorCode.ARTICLE_RECORD_NOT_FOUND,
                    "语境精读记录不存在: " + recordId);
        }
        return record;
    }

    private JsonNode readParsedNode(LearningArticleStudyRecord record) {
        if (!StringUtils.hasText(record.getParsedJson())) {
            throw articleInvalid("语境精读记录缺少结构化学习材料");
        }
        try {
            return objectMapper.readTree(record.getParsedJson());
        } catch (Exception ex) {
            throw articleInvalid("语境精读记录无法解析，请重新生成");
        }
    }

    private String normalizeStage(String value) {
        String stage = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!List.of(ArticleConstants.STAGE_READING,
                ArticleConstants.STAGE_VOCABULARY,
                ArticleConstants.STAGE_CHECK).contains(stage)) {
            throw LearningAssistantException.badRequest(
                    LearningErrorCode.ARTICLE_STAGE_INVALID,
                    "不支持的语境精读阶段: " + value);
        }
        return stage;
    }

    private String articleTitle(LearningArticleStudyRecord record) {
        try {
            return StrUtil.blankToDefault(text(objectMapper.readTree(record.getParsedJson()), "title"), "未命名精读材料");
        } catch (Exception ignored) {
            return "未命名精读材料";
        }
    }

    private String requiredText(JsonNode node, String... keys) {
        String value = text(node, keys);
        if (!StringUtils.hasText(value)) {
            throw articleInvalid("AI 返回的语境精读材料缺少字段: " + String.join("/", keys));
        }
        return value;
    }

    private static String textValue(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return "";
    }

    private static String normalizeAnswer(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private LearningAssistantException articleInvalid(String message) {
        return LearningAssistantException.externalService(
                LearningErrorCode.ARTICLE_AI_RESPONSE_INVALID,
                message,
                null);
    }

    /**
     * 处理 {@code writeJson} 相关业务。
     */
    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningErrorCode.JSON_SERIALIZE_FAILED,
                    errorMessage,
                    ex);
        }
    }

    /**
     * 处理 {@code requireWordbook} 相关业务。
     */
    private LearningWordbook requireWordbook(Long userId, Long wordbookId) {
        return wordbookService.requireOwnedWordbook(userId, wordbookId);
    }

    /**
     * 处理 {@code resolveAgentCode} 相关业务。
     */
    private String resolveAgentCode(ArticleStudyRequest request) {
        return StringUtils.hasText(request.getAgentCode()) ? request.getAgentCode() : AiScenarioConstants.ARTICLE_AGENT_CODE;
    }

    /**
     * 处理 {@code resolveTemplateCode} 相关业务。
     */
    private String resolveTemplateCode(ArticleStudyRequest request) {
        return StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : AiScenarioConstants.ARTICLE_TEMPLATE_CODE;
    }

    /**
     * 处理 {@code trimToNull} 相关业务。
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * CoreMeaning 类。
     */
    /**
     * 处理 {@code CoreMeaning} 相关业务。
     */
    private record CoreMeaning(String partOfSpeech, String meaning) {
    }

    record PracticeScore(int total, int answered, int correct, int score) {
    }
}
