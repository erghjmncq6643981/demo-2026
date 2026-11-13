package com.chandler.learning.agent.reading.application;

import com.chandler.learning.agent.reading.api.response.RuminationMaterialResponse;
import com.chandler.learning.agent.reading.api.response.RuminationPracticeSentenceResponse;
import com.chandler.learning.agent.reading.api.response.RuminationPracticeSummaryResponse;
import com.chandler.learning.agent.reading.api.response.RuminationProbeResponse;
import com.chandler.learning.agent.reading.api.response.RuminationSentenceTargetSpanResponse;
import com.chandler.learning.agent.reading.domain.entity.LearningRuminationSentencePractice;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识反刍场景材料英文分句与目标词定位器。
 * <p>基于材料正文实时确定性分句并匹配语境中的实际词形，兼顾历史材料免重构直接开启单句巩固。</p>
 */
final class RuminationSentenceExtractor {

    static final int DEFAULT_RECOMMEND_LIMIT = 3;
    private static final int IDEAL_SENTENCE_MAX_LENGTH = 180;
    private static final int LONG_SENTENCE_PENALTY_THRESHOLD = 240;
    private static final String DOT_SENTINEL = "\u0007";

    private static final Pattern PARAGRAPH_SPLIT_PATTERN = Pattern.compile("\\r?\\n(?:\\s*\\r?\\n)+|\\r?\\n+");
    private static final Pattern SENTENCE_SPLIT_PATTERN = Pattern.compile("(?<=[.!?][\"'”’)\\]]?)\\s+");
    private static final Pattern DECIMAL_DOT_PATTERN = Pattern.compile("(?<=\\d)\\.(?=\\d)");
    private static final Pattern ABBREVIATION_DOT_PATTERN = Pattern.compile(
            "\\b(Mr|Mrs|Ms|Dr|Prof|Sr|Jr|St|vs|etc|e\\.g|i\\.e|U\\.S|a\\.m|p\\.m)\\.",
            Pattern.CASE_INSENSITIVE);

    record SplitSentence(int sentenceIndex, int paragraphIndex, String text) {
    }

    private record SentenceCandidate(RuminationPracticeSentenceResponse response, int baseScore) {
    }

    private RuminationSentenceExtractor() {
    }

    /**
     * 将英文正文稳定切分为带段落归属与全局序号的句子列表。
     */
    static List<SplitSentence> splitSentences(String articleContent) {
        if (!StringUtils.hasText(articleContent)) {
            return List.of();
        }
        String[] paragraphs = PARAGRAPH_SPLIT_PATTERN.split(articleContent.trim());
        List<SplitSentence> results = new ArrayList<>();
        int globalIndex = 0;
        for (int pIdx = 0; pIdx < paragraphs.length; pIdx++) {
            String rawParagraph = paragraphs[pIdx] == null ? "" : paragraphs[pIdx].trim();
            if (rawParagraph.isEmpty()) {
                continue;
            }
            String protectedText = DECIMAL_DOT_PATTERN.matcher(rawParagraph).replaceAll(DOT_SENTINEL);
            protectedText = protectAbbreviations(protectedText);
            String[] rawSentences = SENTENCE_SPLIT_PATTERN.split(protectedText);
            for (String rawSentence : rawSentences) {
                String restored = rawSentence.replace(DOT_SENTINEL, ".").trim();
                if (!restored.isEmpty()) {
                    results.add(new SplitSentence(globalIndex++, pIdx, restored));
                }
            }
        }
        return results;
    }

    /**
     * 抽取某句中命中目标词列表的不重叠词形跨度，短语优先于单词以避免局部子串误匹配。
     */
    static List<RuminationSentenceTargetSpanResponse> extractTargetSpans(String sentenceText, List<String> targetWords) {
        if (!StringUtils.hasText(sentenceText) || targetWords == null || targetWords.isEmpty()) {
            return List.of();
        }
        List<String> orderedWords = targetWords.stream()
                .filter(StringUtils::hasText)
                .map(w -> w.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        List<int[]> occupiedRanges = new ArrayList<>();
        List<RuminationSentenceTargetSpanResponse> spans = new ArrayList<>();
        for (String targetWord : orderedWords) {
            Matcher matcher = RuminationWordPatternSupport.buildWordCoveragePattern(targetWord).matcher(sentenceText);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (overlapsExisting(occupiedRanges, start, end)) {
                    continue;
                }
                occupiedRanges.add(new int[]{start, end});
                RuminationSentenceTargetSpanResponse span = new RuminationSentenceTargetSpanResponse();
                span.setTargetWord(targetWord);
                span.setSurfaceForm(sentenceText.substring(start, end));
                span.setStartOffset(start);
                span.setEndOffset(end);
                span.setCopyCount(0);
                span.setClozeAttemptCount(0);
                span.setClozeIndependentPassed(false);
                spans.add(span);
            }
        }
        spans.sort(Comparator.comparingInt(RuminationSentenceTargetSpanResponse::getStartOffset));
        return spans;
    }

    /**
     * 为材料响应装配完整分句、目标词巩固状态、默认推荐 <= 3 句以及汇总指标。
     */
    static void populatePracticeData(
            RuminationMaterialResponse response,
            List<LearningRuminationSentencePractice> practiceRecords) {
        if (response == null) {
            return;
        }
        List<SplitSentence> splitSentences = splitSentences(response.getArticleContent());
        List<String> targetWords = response.getTargetWords() == null ? List.of() : response.getTargetWords();
        Map<String, Boolean> probeCorrectnessByWord = buildProbeCorrectnessMap(response.getProbes());
        Map<String, LearningRuminationSentencePractice> recordByKey = new HashMap<>();
        Set<String> settledWords = new LinkedHashSet<>();

        if (practiceRecords != null) {
            for (LearningRuminationSentencePractice record : practiceRecords) {
                if (record == null || record.getSentenceIndex() == null || !StringUtils.hasText(record.getTargetWord())) {
                    continue;
                }
                String normalizedWord = record.getTargetWord().trim().toLowerCase(Locale.ROOT);
                recordByKey.put(keyOf(record.getSentenceIndex(), normalizedWord), record);
                if (Boolean.TRUE.equals(record.getMasterySettled())
                        || Boolean.TRUE.equals(record.getClozeIndependentPassed())) {
                    settledWords.add(normalizedWord);
                }
            }
        }

        List<RuminationPracticeSentenceResponse> sentenceResponses = new ArrayList<>(splitSentences.size());
        List<SentenceCandidate> candidates = new ArrayList<>();

        for (SplitSentence split : splitSentences) {
            List<RuminationSentenceTargetSpanResponse> spans = extractTargetSpans(split.text(), targetWords);
            int maxCopy = 0;
            boolean primaryPassed = false;
            String primaryWord = null;

            for (RuminationSentenceTargetSpanResponse span : spans) {
                LearningRuminationSentencePractice rec = recordByKey.get(keyOf(split.sentenceIndex(), span.getTargetWord()));
                int copyCount = rec != null && rec.getCopyCount() != null ? rec.getCopyCount() : 0;
                int clozeAttempts = rec != null && rec.getClozeAttemptCount() != null ? rec.getClozeAttemptCount() : 0;
                boolean clozePassed = rec != null && Boolean.TRUE.equals(rec.getClozeIndependentPassed());
                span.setCopyCount(copyCount);
                span.setClozeAttemptCount(clozeAttempts);
                span.setClozeIndependentPassed(clozePassed);
                if (copyCount > maxCopy) {
                    maxCopy = copyCount;
                }
            }

            if (!spans.isEmpty()) {
                RuminationSentenceTargetSpanResponse chosenPrimary = choosePrimarySpan(
                        spans, probeCorrectnessByWord, settledWords);
                primaryWord = chosenPrimary.getTargetWord();
                primaryPassed = spans.stream()
                        .allMatch(span -> Boolean.TRUE.equals(span.getClozeIndependentPassed()));
            }

            RuminationPracticeSentenceResponse sentenceRes = new RuminationPracticeSentenceResponse();
            sentenceRes.setSentenceIndex(split.sentenceIndex());
            sentenceRes.setParagraphIndex(split.paragraphIndex());
            sentenceRes.setSentenceText(split.text());
            sentenceRes.setTargetSpans(spans);
            sentenceRes.setPrimaryTargetWord(primaryWord);
            sentenceRes.setRecommended(false);
            sentenceRes.setRecommendPriority(0);
            sentenceRes.setCopyCount(maxCopy);
            sentenceRes.setClozeIndependentPassed(primaryPassed);
            sentenceResponses.add(sentenceRes);

            if (!spans.isEmpty()) {
                int baseScore = computeBasePriority(split.text(), spans, probeCorrectnessByWord, settledWords, maxCopy);
                sentenceRes.setRecommendPriority(baseScore);
                candidates.add(new SentenceCandidate(sentenceRes, baseScore));
            }
        }

        selectRecommendedSentences(candidates);
        response.setPracticeSentences(sentenceResponses);
        response.setPracticeSummary(buildSummary(sentenceResponses, targetWords.size(), settledWords.size()));
    }

    /**
     * 汇总单篇材料在各句子维度的练习次数与独立巩固词数。
     */
    static RuminationPracticeSummaryResponse buildSummary(
            List<RuminationPracticeSentenceResponse> sentences,
            int totalTargetWords,
            int settledWordsCount) {
        RuminationPracticeSummaryResponse summary = new RuminationPracticeSummaryResponse();
        int totalTargetSentences = 0;
        int recommendedCount = 0;
        int copyCompleted = 0;
        int clozePassed = 0;
        Set<String> passedWords = new HashSet<>();

        if (sentences != null) {
            for (RuminationPracticeSentenceResponse sentence : sentences) {
                List<RuminationSentenceTargetSpanResponse> spans = sentence.getTargetSpans();
                if (spans == null || spans.isEmpty()) {
                    continue;
                }
                totalTargetSentences++;
                if (Boolean.TRUE.equals(sentence.getRecommended())) {
                    recommendedCount++;
                }
                if (sentence.getCopyCount() != null && sentence.getCopyCount() > 0) {
                    copyCompleted++;
                }
                boolean allSpansPassed = true;
                for (RuminationSentenceTargetSpanResponse span : spans) {
                    if (Boolean.TRUE.equals(span.getClozeIndependentPassed())) {
                        if (StringUtils.hasText(span.getTargetWord())) {
                            passedWords.add(span.getTargetWord().trim().toLowerCase(Locale.ROOT));
                        }
                    } else {
                        allSpansPassed = false;
                    }
                }
                if (allSpansPassed || Boolean.TRUE.equals(sentence.getClozeIndependentPassed())) {
                    clozePassed++;
                }
            }
        }

        summary.setTotalTargetSentences(totalTargetSentences);
        summary.setRecommendedCount(recommendedCount);
        summary.setCopyCompletedSentences(copyCompleted);
        summary.setClozePassedSentences(clozePassed);
        summary.setIndependentlySettledWords(Math.max(settledWordsCount, passedWords.size()));
        summary.setTotalTargetWords(totalTargetWords);
        return summary;
    }

    private static String protectAbbreviations(String text) {
        Matcher matcher = ABBREVIATION_DOT_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1).replace(".", DOT_SENTINEL) + DOT_SENTINEL;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean overlapsExisting(List<int[]> occupied, int start, int end) {
        for (int[] range : occupied) {
            if (start < range[1] && end > range[0]) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Boolean> buildProbeCorrectnessMap(List<RuminationProbeResponse> probes) {
        Map<String, Boolean> map = new HashMap<>();
        if (probes == null) {
            return map;
        }
        for (RuminationProbeResponse probe : probes) {
            if (probe == null || !StringUtils.hasText(probe.getTargetWord()) || probe.getIsCorrect() == null) {
                continue;
            }
            String word = probe.getTargetWord().trim().toLowerCase(Locale.ROOT);
            map.merge(word, probe.getIsCorrect(), (existing, next) -> existing && next);
        }
        return map;
    }

    private static RuminationSentenceTargetSpanResponse choosePrimarySpan(
            List<RuminationSentenceTargetSpanResponse> spans,
            Map<String, Boolean> probeCorrectnessByWord,
            Set<String> settledWords) {
        return spans.stream()
                .max(Comparator.comparingInt(span -> {
                    int weight = 0;
                    String word = span.getTargetWord();
                    if (Boolean.FALSE.equals(probeCorrectnessByWord.get(word))) {
                        weight += 80;
                    }
                    if (!Boolean.TRUE.equals(span.getClozeIndependentPassed()) && !settledWords.contains(word)) {
                        weight += 50;
                    }
                    if (span.getCopyCount() == null || span.getCopyCount() == 0) {
                        weight += 10;
                    }
                    return weight;
                }))
                .orElse(spans.get(0));
    }

    private static int computeBasePriority(
            String sentenceText,
            List<RuminationSentenceTargetSpanResponse> spans,
            Map<String, Boolean> probeCorrectnessByWord,
            Set<String> settledWords,
            int sentenceCopyCount) {
        int score = 100;
        boolean hasWrongProbe = spans.stream()
                .anyMatch(span -> Boolean.FALSE.equals(probeCorrectnessByWord.get(span.getTargetWord())));
        boolean hasUnsettledWord = spans.stream()
                .anyMatch(span -> !Boolean.TRUE.equals(span.getClozeIndependentPassed())
                        && !settledWords.contains(span.getTargetWord()));
        if (hasWrongProbe) {
            score += 80;
        }
        if (hasUnsettledWord) {
            score += 50;
        }
        if (sentenceCopyCount == 0) {
            score += 15;
        }
        int len = sentenceText == null ? 0 : sentenceText.length();
        if (len >= 25 && len <= IDEAL_SENTENCE_MAX_LENGTH) {
            score += 20;
        } else if (len > LONG_SENTENCE_PENALTY_THRESHOLD) {
            score -= 25;
        }
        return score;
    }

    private static void selectRecommendedSentences(List<SentenceCandidate> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        List<SentenceCandidate> remaining = new ArrayList<>(candidates);
        Set<String> coveredPrimaryWords = new HashSet<>();
        int picked = 0;

        while (!remaining.isEmpty() && picked < DEFAULT_RECOMMEND_LIMIT) {
            SentenceCandidate best = null;
            int bestAdjusted = Integer.MIN_VALUE;
            for (SentenceCandidate candidate : remaining) {
                RuminationPracticeSentenceResponse sentence = candidate.response();
                boolean introducesNewWord = sentence.getTargetSpans().stream()
                        .anyMatch(span -> !coveredPrimaryWords.contains(span.getTargetWord()));
                int adjusted = candidate.baseScore() + (introducesNewWord ? 35 : 0);
                if (best == null || adjusted > bestAdjusted
                        || (adjusted == bestAdjusted
                        && sentence.getSentenceIndex() < best.response().getSentenceIndex())) {
                    best = candidate;
                    bestAdjusted = adjusted;
                }
            }
            if (best == null) {
                break;
            }
            best.response().setRecommended(true);
            best.response().setRecommendPriority(bestAdjusted);
            best.response().getTargetSpans().forEach(span -> coveredPrimaryWords.add(span.getTargetWord()));
            remaining.remove(best);
            picked++;
        }
    }

    private static String keyOf(int sentenceIndex, String targetWord) {
        return sentenceIndex + ":" + targetWord;
    }
}
