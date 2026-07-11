package com.chandler.learning.agent.service.vocabulary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyBestMatchResponse;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyStudyRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyStudyResponse;
import com.chandler.learning.agent.domain.entity.vocabulary.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.domain.enums.LearningScene;
import com.chandler.learning.agent.domain.enums.SystemLogType;
import com.chandler.learning.agent.domain.enums.VocabularyMatchType;
import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.mapper.vocabulary.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.service.AiChatService;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.UserDisplayNameService;
import com.chandler.learning.agent.service.learning.VocabularyInsightService;
import com.chandler.learning.agent.support.LearningConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 英语词汇学习服务。
 * <p>
 * 优先读取本地结构化缓存；缓存缺失或强制刷新时再调用 AI，避免重复消耗模型额度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnglishVocabularyStudyService {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final EnglishVocabularyStudyRecordMapper recordMapper;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;
    private final VocabularyInsightService vocabularyInsightService;
    private final SystemLogService systemLogService;
    private final UserDisplayNameService userDisplayNameService;

    public VocabularyStudyResponse study(VocabularyStudyRequest request) {
        String normalizedTerm = normalize(request.getTerm());
        if (!StringUtils.hasText(normalizedTerm)) {
            throw LearningAssistantException.badRequest(
                    LearningConstants.ErrorCode.VOCABULARY_EMPTY,
                    "单词不能为空");
        }

        boolean forceRefresh = Boolean.TRUE.equals(request.getForceRefresh());
        EnglishVocabularyStudyRecord existing = findByNormalizedTerm(normalizedTerm);
        if (existing != null && !forceRefresh) {
            touch(existing);
            vocabularyInsightService.syncInsights(existing);
            systemLogService.record(null, SystemLogType.CACHE, "读取词汇缓存", normalizedTerm);
            log.debug("词汇缓存命中 term={} recordId={} lookupCount={}",
                    normalizedTerm,
                    existing.getId(),
                    existing.getLookupCount());
            return toResponse(existing, true);
        }

        log.debug("开始生成词汇学习卡片 term={} forceRefresh={} modelConfigId={}",
                normalizedTerm,
                forceRefresh,
                request.getModelConfigId());
        AgentChatResponse chatResponse = aiChat(request, normalizedTerm);
        EnglishVocabularyStudyRecord record = existing == null ? new EnglishVocabularyStudyRecord() : existing;
        record.setTerm(request.getTerm().trim());
        record.setNormalizedTerm(normalizedTerm);
        record.setAgentCode(resolveAgentCode(request));
        record.setTemplateCode(resolveTemplateCode(request));
        record.setProvider(chatResponse.getModelProvider());
        record.setModelName(chatResponse.getModelName());
        record.setSessionId(chatResponse.getSessionId());
        record.setRawContent(chatResponse.getContent());
        record.setParsedJson(extractJson(chatResponse.getContent()));
        record.setTokenUsage(chatResponse.getTokenUsage());
        record.setCostTime(chatResponse.getCostTime());
        record.setLookupCount(existing == null || existing.getLookupCount() == null
                ? LearningConstants.Vocabulary.DEFAULT_LOOKUP_COUNT
                : existing.getLookupCount() + LearningConstants.Vocabulary.DEFAULT_LOOKUP_COUNT);
        record.setLastLookupTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());

        if (existing == null) {
            record.setCreateTime(LocalDateTime.now());
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }

        vocabularyInsightService.syncInsights(record);
        systemLogService.record(null, SystemLogType.AI, "AI 生成词汇卡片", normalizedTerm);
        log.info("用户「{}」使用「{} / {}」生成了单词「{}」的学习卡片，是否刷新缓存：{}",
                userDisplayNameService.currentUserName(),
                record.getProvider(),
                record.getModelName(),
                normalizedTerm,
                existing != null);
        log.debug("词汇学习卡片已保存 term={} recordId={} provider={} model={} cacheRefresh={}",
                normalizedTerm, record.getId(), record.getProvider(), record.getModelName(), existing != null);
        return toResponse(record, false);
    }

    public VocabularyStudyResponse detail(String term) {
        String normalizedTerm = normalize(term);
        EnglishVocabularyStudyRecord record = findByNormalizedTerm(normalizedTerm);
        log.debug("查询词汇学习缓存 term={} found={}", normalizedTerm, record != null);
        return record == null ? null : toResponse(record, true);
    }

    public VocabularyBestMatchResponse bestMatch(String term) {
        String normalizedTerm = normalize(term);
        if (!StringUtils.hasText(normalizedTerm)) {
            return null;
        }
        EnglishVocabularyStudyRecord exact = findByNormalizedTerm(normalizedTerm);
        if (exact != null) {
            log.debug("词汇最匹配查询命中精确结果 query={} recordId={}", normalizedTerm, exact.getId());
            return toBestMatchResponse(normalizedTerm, exact, LearningConstants.Vocabulary.EXACT_MATCH_SCORE,
                    VocabularyMatchType.EXACT.getCode());
        }

        List<EnglishVocabularyStudyRecord> candidates = recordMapper.selectList(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                .orderByDesc(EnglishVocabularyStudyRecord::getUpdateTime)
                .last("LIMIT " + LearningConstants.Vocabulary.FUZZY_MATCH_CANDIDATE_LIMIT));
        VocabularyBestMatchResponse response = candidates.stream()
                .map(candidate -> new MatchCandidate(candidate, matchScore(normalizedTerm, candidate.getNormalizedTerm())))
                .filter(candidate -> candidate.score() >= LearningConstants.Vocabulary.FUZZY_MATCH_MIN_SCORE)
                .max(Comparator.comparingInt(MatchCandidate::score)
                        .thenComparing(candidate -> candidate.record().getLookupCount() == null
                                ? LearningConstants.ZERO
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
     * 将词汇学习请求转换成 Agent 对话请求，模型输出仍由学习卡片解析逻辑统一处理。
     */
    private AgentChatResponse aiChat(VocabularyStudyRequest request, String normalizedTerm) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("term", normalizedTerm);

        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setAgentCode(resolveAgentCode(request));
        chatRequest.setTemplateCode(resolveTemplateCode(request));
        chatRequest.setModelConfigId(request.getModelConfigId());
        chatRequest.setTitle("Vocabulary - " + normalizedTerm);
        chatRequest.setBusinessType(LearningConstants.ChatSession.BUSINESS_TYPE_LEARNING);
        chatRequest.setBusinessId(LearningScene.ENGLISH_VOCABULARY.getCode());
        chatRequest.setSceneCode(LearningScene.ENGLISH_VOCABULARY.getCode());
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
                .last(LearningConstants.SQL_LIMIT_ONE));
    }

    private void touch(EnglishVocabularyStudyRecord record) {
        record.setLookupCount(record.getLookupCount() == null
                ? LearningConstants.Vocabulary.DEFAULT_LOOKUP_COUNT
                : record.getLookupCount() + LearningConstants.Vocabulary.DEFAULT_LOOKUP_COUNT);
        record.setLastLookupTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
    }

    private VocabularyStudyResponse toResponse(EnglishVocabularyStudyRecord record, boolean cacheHit) {
        VocabularyStudyResponse response = new VocabularyStudyResponse();
        response.setId(record.getId());
        response.setTerm(record.getTerm());
        response.setNormalizedTerm(record.getNormalizedTerm());
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
        response.setRecord(toResponse(record, true));
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
     * 兼容模型返回纯 JSON 或 Markdown 代码块两种格式，最终落库为标准 JSON 字符串。
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
            String candidate = matcher.group();
            try {
                JsonNode jsonNode = objectMapper.readTree(candidate);
                return objectMapper.writeValueAsString(jsonNode);
            } catch (Exception ex) {
                log.warn("词汇模型响应 JSON 提取失败: {}", ex.getMessage());
                return null;
            }
        }
    }

    private String normalize(String term) {
        return term == null ? "" : term.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private int matchScore(String query, String candidate) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(candidate)) {
            return LearningConstants.Vocabulary.MIN_MATCH_SCORE;
        }
        if (query.equals(candidate)) {
            return LearningConstants.Vocabulary.EXACT_MATCH_SCORE;
        }
        int distance = levenshtein(query, candidate);
        int maxLength = Math.max(query.length(), candidate.length());
        int score = Math.max(LearningConstants.Vocabulary.MIN_MATCH_SCORE,
                (int) Math.round((1D - (double) distance / maxLength) * LearningConstants.Vocabulary.EXACT_MATCH_SCORE));
        if (candidate.startsWith(query) || query.startsWith(candidate)) {
            score += LearningConstants.Vocabulary.PREFIX_SCORE_BOOST;
        }
        if (query.charAt(0) == candidate.charAt(0)) {
            score += LearningConstants.Vocabulary.SAME_INITIAL_SCORE_BOOST;
        }
        if (commonPrefixLength(query, candidate) >= LearningConstants.Vocabulary.COMMON_PREFIX_MIN_LENGTH) {
            score += LearningConstants.Vocabulary.COMMON_PREFIX_SCORE_BOOST;
        }
        if (candidate.contains(query) || query.contains(candidate)) {
            score += LearningConstants.Vocabulary.CONTAINS_SCORE_BOOST;
        }
        return Math.min(score, LearningConstants.Vocabulary.FUZZY_MATCH_MAX_SCORE);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + LearningConstants.Vocabulary.EDIT_DISTANCE_INSERT_DELETE_COST];
        int[] current = new int[right.length() + LearningConstants.Vocabulary.EDIT_DISTANCE_INSERT_DELETE_COST];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[LearningConstants.ZERO] = row;
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - LearningConstants.Vocabulary.EDIT_DISTANCE_INSERT_DELETE_COST)
                        == right.charAt(column - LearningConstants.Vocabulary.EDIT_DISTANCE_INSERT_DELETE_COST)
                        ? LearningConstants.Vocabulary.EDIT_DISTANCE_SAME_COST
                        : LearningConstants.Vocabulary.EDIT_DISTANCE_REPLACE_COST;
                current[column] = Math.min(Math.min(
                        current[column - LearningConstants.Vocabulary.EDIT_DISTANCE_INSERT_DELETE_COST] + LearningConstants.Vocabulary.EDIT_DISTANCE_INSERT_DELETE_COST,
                        previous[column] + LearningConstants.Vocabulary.EDIT_DISTANCE_INSERT_DELETE_COST),
                        previous[column - LearningConstants.Vocabulary.EDIT_DISTANCE_INSERT_DELETE_COST] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private int commonPrefixLength(String left, String right) {
        int length = Math.min(left.length(), right.length());
        int index = LearningConstants.ZERO;
        while (index < length && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    private String resolveAgentCode(VocabularyStudyRequest request) {
        return StringUtils.hasText(request.getAgentCode()) ? request.getAgentCode() : LearningConstants.VOCABULARY_AGENT_CODE;
    }

    private String resolveTemplateCode(VocabularyStudyRequest request) {
        return StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : LearningConstants.VOCABULARY_TEMPLATE_CODE;
    }

    private record MatchCandidate(EnglishVocabularyStudyRecord record, int score) {
    }
}
