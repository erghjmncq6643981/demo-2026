package com.chandler.learning.agent.service.learning;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.dto.learning.ArticleStudyRequest;
import com.chandler.learning.agent.domain.dto.learning.ArticleStudyResponse;
import com.chandler.learning.agent.domain.dto.learning.ArticleStudyWordResponse;
import com.chandler.learning.agent.domain.entity.learning.LearningArticleStudyRecord;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbook;
import com.chandler.learning.agent.domain.entity.learning.LearningWordbookEntry;
import com.chandler.learning.agent.domain.enums.ArticleDifficulty;
import com.chandler.learning.agent.domain.enums.ArticleWordCountRange;
import com.chandler.learning.agent.domain.enums.LearningScene;
import com.chandler.learning.agent.domain.enums.ReviewStatus;
import com.chandler.learning.agent.domain.enums.SystemLogType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.learning.LearningArticleStudyRecordMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordbookEntryMapper;
import com.chandler.learning.agent.mapper.learning.LearningWordbookMapper;
import com.chandler.learning.agent.service.AiChatService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文章学习服务。
 * <p>
 * 根据用户从单词本中选择的词汇生成英语学习文章，并保存 AI 原始回复和结构化解析结果。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleStudyService {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final LearningArticleStudyRecordMapper articleStudyRecordMapper;
    private final LearningWordbookMapper wordbookMapper;
    private final LearningWordbookEntryMapper entryMapper;
    private final AiChatService aiChatService;
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
        String selectedTermsJson = writeJson(selectedWords, "文章学习词汇摘要序列化失败");
        String selectedTermHash = hash(userId, wordbook.getId(), selectedWords, wordCountRange, difficulty, remark);

        boolean forceRefresh = Boolean.TRUE.equals(request.getForceRefresh());
        LearningArticleStudyRecord existing = forceRefresh ? null : findCached(userId, wordbook.getId(), selectedTermHash);
        if (existing != null) {
            existing.touch(LocalDateTime.now());
            articleStudyRecordMapper.updateById(existing);
            systemLogService.record(userId, SystemLogType.CACHE, "读取文章学习缓存",
                    wordbook.getName() + "，" + selectedWords.size() + " 个单词");
            log.info("用户「{}」打开了单词本「{}」中 {} 个单词的文章学习缓存",
                    userDisplayNameService.userName(userId),
                    wordbook.getName(),
                    selectedWords.size());
            log.debug("文章学习缓存命中 userId={} wordbookId={} recordId={} hash={}",
                    userId, wordbook.getId(), existing.getId(), selectedTermHash);
            return toResponse(existing, true);
        }

        log.debug("开始生成文章学习材料 userId={} wordbookId={} wordCountRange={} difficulty={} forceRefresh={} words={}",
                userId,
                wordbook.getId(),
                wordCountRange.getCode(),
                difficulty.getCode(),
                forceRefresh,
                selectedWords.stream().map(ArticleStudyWordResponse::getNormalizedTerm).toList());
        AgentChatResponse chatResponse = aiChat(request, selectedWords, wordCountRange, difficulty, remark);
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
        record.applyAiResult(chatResponse, extractJson(chatResponse.getContent()), now);
        articleStudyRecordMapper.insert(record);

        systemLogService.record(userId, SystemLogType.AI, "生成文章学习材料",
                wordbook.getName() + "，" + selectedWords.size() + " 个单词，" + wordCountRange.getLabel() + "，" + difficulty.getLabel());
        log.info("用户「{}」基于单词本「{}」中的 {} 个单词生成了「{}」「{}」难度的文章学习材料",
                userDisplayNameService.userName(userId),
                wordbook.getName(),
                selectedWords.size(),
                wordCountRange.getLabel(),
                difficulty.getLabel());
        log.debug("文章学习材料已保存 userId={} wordbookId={} recordId={} sessionId={} provider={} model={}",
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
    public List<ArticleStudyResponse> listRecords(Long userId, Long wordbookId, Integer limit) {
        Long resolvedWordbookId = wordbookId == null ? null : requireWordbook(userId, wordbookId).getId();
        int resolvedLimit = Math.max(LearningConstants.Article.MIN_HISTORY_LIMIT,
                Math.min(limit == null ? LearningConstants.Article.DEFAULT_HISTORY_LIMIT : limit,
                        LearningConstants.Article.MAX_HISTORY_LIMIT));
        return articleStudyRecordMapper.selectList(new LambdaQueryWrapper<LearningArticleStudyRecord>()
                        .eq(LearningArticleStudyRecord::getUserId, userId)
                        .eq(resolvedWordbookId != null, LearningArticleStudyRecord::getWordbookId, resolvedWordbookId)
                        .eq(LearningArticleStudyRecord::getDeleted, false)
                        .orderByDesc(LearningArticleStudyRecord::getUpdateTime)
                        .last("LIMIT " + resolvedLimit))
                .stream()
                .map(record -> toResponse(record, true))
                .toList();
    }

    /**
     * 查询 {@code detail} 相关业务。
     */
    public ArticleStudyResponse detail(Long userId, Long recordId) {
        LearningArticleStudyRecord record = articleStudyRecordMapper.selectOne(new LambdaQueryWrapper<LearningArticleStudyRecord>()
                .eq(LearningArticleStudyRecord::getId, recordId)
                .eq(LearningArticleStudyRecord::getUserId, userId)
                .eq(LearningArticleStudyRecord::getDeleted, false)
                .last(LearningConstants.SQL_LIMIT_ONE));
        if (record == null) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.ARTICLE_RECORD_NOT_FOUND,
                    "文章学习记录不存在: " + recordId);
        }
        record.touch(LocalDateTime.now());
        articleStudyRecordMapper.updateById(record);
        log.debug("文章学习记录详情已读取 userId={} recordId={}", userId, recordId);
        return toResponse(record, true);
    }

    /**
     * 处理 {@code aiChat} 相关业务。
     */
    private AgentChatResponse aiChat(ArticleStudyRequest request, List<ArticleStudyWordResponse> selectedWords,
                                     ArticleWordCountRange wordCountRange, ArticleDifficulty difficulty, String remark) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("words", selectedWords);
        variables.put("word_count_range", wordCountRange.getLabel());
        variables.put("word_count_min", wordCountRange.getMinWords());
        variables.put("word_count_max", wordCountRange.getMaxWords());
        variables.put("difficulty", difficulty.getLabel());
        variables.put("difficulty_code", difficulty.getCode());
        variables.put("difficulty_prompt", difficulty.getPrompt());
        variables.put("remark", StrUtil.blankToDefault(remark, "无特别备注"));

        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setAgentCode(resolveAgentCode(request));
        chatRequest.setTemplateCode(resolveTemplateCode(request));
        chatRequest.setModelConfigId(request.getModelConfigId());
        chatRequest.setTitle(LearningScene.ENGLISH_ARTICLE.getTitle());
        chatRequest.setBusinessType(LearningConstants.ChatSession.BUSINESS_TYPE_LEARNING);
        chatRequest.setBusinessId(LearningScene.ENGLISH_ARTICLE.getCode());
        chatRequest.setSceneCode(LearningScene.ENGLISH_ARTICLE.getCode());
        chatRequest.setMessage("请基于用户选择的单词生成英语文章学习材料。");
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
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    /**
     * 处理 {@code normalizeEntryIds} 相关业务。
     */
    private List<Long> normalizeEntryIds(List<Long> entryIds) {
        if (CollUtil.isEmpty(entryIds)) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.ARTICLE_WORDS_EMPTY,
                    "请选择要生成文章的单词");
        }
        List<Long> normalized = entryIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalized.size() < LearningConstants.Article.MIN_SELECTED_WORDS) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.ARTICLE_WORDS_EMPTY,
                    "请选择要生成文章的单词");
        }
        if (normalized.size() > LearningConstants.Article.MAX_SELECTED_WORDS) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.ARTICLE_WORD_LIMIT_EXCEEDED,
                    "一次最多选择 " + LearningConstants.Article.MAX_SELECTED_WORDS + " 个单词生成文章");
        }
        return normalized;
    }

    /**
     * 处理 {@code requireEntries} 相关业务。
     */
    private List<LearningWordbookEntry> requireEntries(Long userId, Long wordbookId, List<Long> entryIds) {
        List<LearningWordbookEntry> entries = entryMapper.selectList(new LambdaQueryWrapper<LearningWordbookEntry>()
                .eq(LearningWordbookEntry::getUserId, userId)
                .eq(LearningWordbookEntry::getWordbookId, wordbookId)
                .eq(LearningWordbookEntry::getDeleted, false)
                .in(LearningWordbookEntry::getId, entryIds));
        Map<Long, LearningWordbookEntry> entryMap = entries.stream()
                .collect(Collectors.toMap(LearningWordbookEntry::getId, entry -> entry, (left, right) -> left, LinkedHashMap::new));
        List<LearningWordbookEntry> ordered = entryIds.stream()
                .map(entryMap::get)
                .filter(Objects::nonNull)
                .toList();
        if (ordered.size() != entryIds.size()) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.ARTICLE_WORDS_INVALID,
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
            log.warn("文章学习词汇核心含义读取失败 entryId={} term={} error={}",
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
            log.warn("文章学习词汇摘要读取失败 recordId={} error={}", record.getId(), ex.getMessage());
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
            log.warn("文章学习结构化 JSON 读取失败 recordId={} error={}", record.getId(), ex.getMessage());
            return null;
        }
    }

    /**
     * 处理 {@code extractJson} 相关业务。
     */
    private String extractJson(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String cleaned = content.replace("```json", "").replace("```", "").trim();
        try {
            JsonNode jsonNode = objectMapper.readTree(cleaned);
            return objectMapper.writeValueAsString(jsonNode);
        } catch (Exception ignored) {
            Matcher matcher = JSON_BLOCK_PATTERN.matcher(cleaned);
            if (!matcher.find()) {
                return null;
            }
            try {
                JsonNode jsonNode = objectMapper.readTree(matcher.group());
                return objectMapper.writeValueAsString(jsonNode);
            } catch (Exception ex) {
                log.warn("文章学习模型响应 JSON 提取失败: {}", ex.getMessage());
                return null;
            }
        }
    }

    /**
     * 处理 {@code writeJson} 相关业务。
     */
    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw LearningAssistantException.system(
                    LearningConstants.ErrorCode.JSON_SERIALIZE_FAILED,
                    errorMessage,
                    ex);
        }
    }

    /**
     * 处理 {@code requireWordbook} 相关业务。
     */
    private LearningWordbook requireWordbook(Long userId, Long wordbookId) {
        LearningWordbook wordbook = wordbookMapper.selectById(wordbookId);
        if (wordbook == null || Boolean.TRUE.equals(wordbook.getDeleted()) || !wordbook.getUserId().equals(userId)) {
            throw LearningAssistantException.notFound(
                    LearningConstants.ErrorCode.WORDBOOK_NOT_FOUND,
                    "单词本不存在: " + wordbookId);
        }
        return wordbook;
    }

    /**
     * 处理 {@code resolveAgentCode} 相关业务。
     */
    private String resolveAgentCode(ArticleStudyRequest request) {
        return StringUtils.hasText(request.getAgentCode()) ? request.getAgentCode() : LearningConstants.ARTICLE_AGENT_CODE;
    }

    /**
     * 处理 {@code resolveTemplateCode} 相关业务。
     */
    private String resolveTemplateCode(ArticleStudyRequest request) {
        return StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : LearningConstants.ARTICLE_TEMPLATE_CODE;
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
}
