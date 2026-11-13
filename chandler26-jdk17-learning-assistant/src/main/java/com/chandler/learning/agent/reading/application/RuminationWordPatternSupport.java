package com.chandler.learning.agent.reading.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识反刍英文目标词词形匹配工具。
 * <p>集中维护词干及常见规则屈折变化匹配，同时供 AI 正文覆盖校验与按句遮挡提词复用。</p>
 */
final class RuminationWordPatternSupport {

    private RuminationWordPatternSupport() {
    }

    /**
     * 为单词或英文短语构建大小写无关、支持常见词形变化的整词匹配正则。
     */
    static Pattern buildWordCoveragePattern(String rawWord) {
        String word = rawWord == null ? "" : rawWord.trim().toLowerCase(Locale.ROOT);
        if (word.isEmpty()) {
            return Pattern.compile("$^");
        }
        if (word.contains(" ")) {
            String phraseRegex = Arrays.stream(word.split("\\s+"))
                    .map(Pattern::quote)
                    .collect(Collectors.joining("\\s+"));
            return Pattern.compile("(?<![a-zA-Z])" + phraseRegex + "(?:s|es|ed|ing)?(?![a-zA-Z])",
                    Pattern.CASE_INSENSITIVE);
        }
        StringBuilder alts = new StringBuilder(Pattern.quote(word) + "(?:s|es|ed|ing|d|er|est|ly)?");
        if (word.length() > 2 && word.endsWith("y")) {
            alts.append('|').append(Pattern.quote(word.substring(0, word.length() - 1))).append("(?:ies|ied|ying)");
        }
        if (word.length() > 2 && word.endsWith("e") && !word.endsWith("ee")) {
            alts.append('|').append(Pattern.quote(word.substring(0, word.length() - 1))).append("(?:ed|ing|es)");
        }
        char last = word.charAt(word.length() - 1);
        char prev = word.length() >= 2 ? word.charAt(word.length() - 2) : ' ';
        if (word.length() >= 3 && !isEnglishVowel(last) && isEnglishVowel(prev)) {
            alts.append('|').append(Pattern.quote(word + last)).append("(?:ed|ing|er)");
        }
        return Pattern.compile("(?<![a-zA-Z])(?:" + alts + ")(?![a-zA-Z])", Pattern.CASE_INSENSITIVE);
    }

    private static boolean isEnglishVowel(char ch) {
        return ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u';
    }
}
