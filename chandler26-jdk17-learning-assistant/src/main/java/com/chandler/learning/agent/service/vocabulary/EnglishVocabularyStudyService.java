package com.chandler.learning.agent.service.vocabulary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chandler.learning.agent.domain.dto.AgentChatRequest;
import com.chandler.learning.agent.domain.dto.AgentChatResponse;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyBestMatchResponse;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyStudyRequest;
import com.chandler.learning.agent.domain.dto.vocabulary.VocabularyStudyResponse;
import com.chandler.learning.agent.domain.entity.vocabulary.EnglishVocabularyStudyRecord;
import com.chandler.learning.agent.mapper.vocabulary.EnglishVocabularyStudyRecordMapper;
import com.chandler.learning.agent.service.AiChatService;
import com.chandler.learning.agent.service.learning.SystemLogService;
import com.chandler.learning.agent.service.learning.VocabularyInsightService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
 */
@Service
@RequiredArgsConstructor
public class EnglishVocabularyStudyService {

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("\\{[\\s\\S]*}");

    private final EnglishVocabularyStudyRecordMapper recordMapper;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;
    private final VocabularyInsightService vocabularyInsightService;
    private final SystemLogService systemLogService;

    public VocabularyStudyResponse study(VocabularyStudyRequest request) {
        String normalizedTerm = normalize(request.getTerm());
        if (!StringUtils.hasText(normalizedTerm)) {
            throw new IllegalArgumentException("单词不能为空");
        }

        boolean forceRefresh = Boolean.TRUE.equals(request.getForceRefresh());
        EnglishVocabularyStudyRecord existing = findByNormalizedTerm(normalizedTerm);
        if (existing != null && !forceRefresh) {
            touch(existing);
            vocabularyInsightService.syncInsights(existing);
            systemLogService.record(null, "cache", "读取词汇缓存", normalizedTerm);
            return toResponse(existing, true);
        }

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
        record.setLookupCount(existing == null || existing.getLookupCount() == null ? 1 : existing.getLookupCount() + 1);
        record.setLastLookupTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());

        if (existing == null) {
            record.setCreateTime(LocalDateTime.now());
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }

        vocabularyInsightService.syncInsights(record);
        systemLogService.record(null, "ai", "AI 生成词汇卡片", normalizedTerm);
        return toResponse(record, false);
    }

    public VocabularyStudyResponse detail(String term) {
        EnglishVocabularyStudyRecord record = findByNormalizedTerm(normalize(term));
        return record == null ? null : toResponse(record, true);
    }

    public VocabularyBestMatchResponse bestMatch(String term) {
        String normalizedTerm = normalize(term);
        if (!StringUtils.hasText(normalizedTerm)) {
            return null;
        }
        EnglishVocabularyStudyRecord exact = findByNormalizedTerm(normalizedTerm);
        if (exact != null) {
            return toBestMatchResponse(normalizedTerm, exact, 100, "exact");
        }

        List<EnglishVocabularyStudyRecord> candidates = recordMapper.selectList(new LambdaQueryWrapper<EnglishVocabularyStudyRecord>()
                .orderByDesc(EnglishVocabularyStudyRecord::getUpdateTime)
                .last("LIMIT 1000"));
        return candidates.stream()
                .map(candidate -> new MatchCandidate(candidate, matchScore(normalizedTerm, candidate.getNormalizedTerm())))
                .filter(candidate -> candidate.score() >= 45)
                .max(Comparator.comparingInt(MatchCandidate::score)
                        .thenComparing(candidate -> candidate.record().getLookupCount() == null ? 0 : candidate.record().getLookupCount()))
                .map(candidate -> toBestMatchResponse(normalizedTerm, candidate.record(), candidate.score(), "fuzzy"))
                .orElse(null);
    }

    private AgentChatResponse aiChat(VocabularyStudyRequest request, String normalizedTerm) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("term", normalizedTerm);

        AgentChatRequest chatRequest = new AgentChatRequest();
        chatRequest.setAgentCode(resolveAgentCode(request));
        chatRequest.setTemplateCode(resolveTemplateCode(request));
        chatRequest.setModelConfigId(request.getModelConfigId());
        chatRequest.setTitle("Vocabulary - " + normalizedTerm);
        chatRequest.setBusinessType("vocabulary");
        chatRequest.setBusinessId(normalizedTerm);
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
                .last("LIMIT 1"));
    }

    private void touch(EnglishVocabularyStudyRecord record) {
        record.setLookupCount(record.getLookupCount() == null ? 1 : record.getLookupCount() + 1);
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
            return null;
        }
    }

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
                return null;
            }
        }
    }

    private String normalize(String term) {
        return term == null ? "" : term.trim().replaceAll("\\s+", " ").toLowerCase();
    }

    private int matchScore(String query, String candidate) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(candidate)) {
            return 0;
        }
        if (query.equals(candidate)) {
            return 100;
        }
        int distance = levenshtein(query, candidate);
        int maxLength = Math.max(query.length(), candidate.length());
        int score = Math.max(0, (int) Math.round((1D - (double) distance / maxLength) * 100));
        if (candidate.startsWith(query) || query.startsWith(candidate)) {
            score += 12;
        }
        if (query.charAt(0) == candidate.charAt(0)) {
            score += 6;
        }
        if (commonPrefixLength(query, candidate) >= 2) {
            score += 8;
        }
        if (candidate.contains(query) || query.contains(candidate)) {
            score += 8;
        }
        return Math.min(score, 99);
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(
                        current[column - 1] + 1,
                        previous[column] + 1),
                        previous[column - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private int commonPrefixLength(String left, String right) {
        int length = Math.min(left.length(), right.length());
        int index = 0;
        while (index < length && left.charAt(index) == right.charAt(index)) {
            index++;
        }
        return index;
    }

    private String resolveAgentCode(VocabularyStudyRequest request) {
        return StringUtils.hasText(request.getAgentCode()) ? request.getAgentCode() : "english_vocabulary";
    }

    private String resolveTemplateCode(VocabularyStudyRequest request) {
        return StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : "english_vocab_card_json";
    }

    private record MatchCandidate(EnglishVocabularyStudyRecord record, int score) {
    }
}
