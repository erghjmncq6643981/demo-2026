package com.chandler.learning.agent.vocabulary.application;

import com.chandler.learning.agent.exception.LearningAssistantException;
import com.chandler.learning.agent.common.exception.LearningErrorCode;
import com.chandler.learning.agent.vocabulary.domain.constant.VocabularyImportConstants;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按表头解析 Markdown 词表，只保留来源“序号”，忽略额外的 No. 列。
 */
@Component
public class MarkdownVocabularyParser {

    private static final String HEADER_ORDER = "序号";
    private static final String HEADER_WORD = "word";
    private static final String HEADER_PHONETIC = "音标";
    private static final String HEADER_DEFINITION = "释义";

    /**
     * 解析 Markdown 表格并返回结构化词条。
     */
    public List<ParsedVocabulary> parse(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            throw invalid("Markdown 内容不能为空");
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        Map<String, Integer> columns = null;
        List<ParsedVocabulary> result = new ArrayList<>();
        for (String line : lines) {
            if (!line.contains("|")) {
                continue;
            }
            List<String> cells = splitRow(line);
            if (columns == null) {
                Map<String, Integer> candidate = headerColumns(cells);
                if (hasRequiredHeaders(candidate)) {
                    columns = candidate;
                }
                continue;
            }
            if (isSeparator(cells)) {
                continue;
            }
            ParsedVocabulary parsed = parseRow(cells, columns);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        if (columns == null) {
            throw invalid("未找到包含“序号、Word、音标、释义”的 Markdown 表头");
        }
        if (result.isEmpty()) {
            throw invalid("Markdown 表格中没有可导入词条");
        }
        Set<Integer> sourceOrders = new HashSet<>();
        result.stream()
                .map(ParsedVocabulary::sourceOrder)
                .filter(sourceOrder -> !sourceOrders.add(sourceOrder))
                .findFirst()
                .ifPresent(sourceOrder -> {
                    throw invalid("词表序号重复: " + sourceOrder);
                });
        return List.copyOf(result);
    }

    private ParsedVocabulary parseRow(List<String> cells, Map<String, Integer> columns) {
        String orderText = cell(cells, columns.get(HEADER_ORDER));
        String term = cleanCell(cell(cells, columns.get(HEADER_WORD)));
        if (!StringUtils.hasText(orderText) && !StringUtils.hasText(term)) {
            return null;
        }
        int sourceOrder;
        try {
            sourceOrder = Integer.parseInt(orderText.trim());
        } catch (Exception ex) {
            throw invalid("词表序号不是有效整数: " + orderText);
        }
        if (!StringUtils.hasText(term)) {
            throw invalid("序号 " + sourceOrder + " 的 Word 为空");
        }
        String suggestion = suggestSplitCorrection(term);
        boolean suspicious = suggestion != null && !suggestion.equals(term);
        List<String> warnings = suspicious
                ? List.of(VocabularyImportConstants.WARNING_SUSPICIOUS_SPLIT)
                : List.of();
        return new ParsedVocabulary(
                sourceOrder,
                term,
                normalize(term),
                suggestion,
                cleanCell(cell(cells, columns.get(HEADER_PHONETIC))),
                cleanCell(cell(cells, columns.get(HEADER_DEFINITION))),
                suspicious,
                warnings);
    }

    private String suggestSplitCorrection(String term) {
        List<String> tokens = new ArrayList<>(Arrays.asList(term.trim().split("\\s+")));
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (token.length() != 1 || !Character.isLetter(token.charAt(0))) {
                continue;
            }
            if (index == tokens.size() - 1 && index > 0 && letterCount(tokens.get(index - 1)) >= 4) {
                tokens.set(index - 1, tokens.get(index - 1) + token);
                tokens.remove(index);
                return String.join(" ", tokens);
            }
            if (index < tokens.size() - 1
                    && !"a".equalsIgnoreCase(token)
                    && !"i".equalsIgnoreCase(token)
                    && letterCount(tokens.get(index + 1)) >= 4) {
                tokens.set(index, token + tokens.get(index + 1));
                tokens.remove(index + 1);
                return String.join(" ", tokens);
            }
        }
        return null;
    }

    private int letterCount(String value) {
        return (int) value.chars().filter(Character::isLetter).count();
    }

    private Map<String, Integer> headerColumns(List<String> cells) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int index = 0; index < cells.size(); index++) {
            String name = cleanCell(cells.get(index)).toLowerCase(Locale.ROOT);
            columns.put(name, index);
        }
        return columns;
    }

    private boolean hasRequiredHeaders(Map<String, Integer> columns) {
        return columns.containsKey(HEADER_ORDER)
                && columns.containsKey(HEADER_WORD)
                && columns.containsKey(HEADER_PHONETIC)
                && columns.containsKey(HEADER_DEFINITION);
    }

    private List<String> splitRow(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return Arrays.stream(trimmed.split("(?<!\\\\)\\|", -1))
                .map(String::trim)
                .toList();
    }

    private boolean isSeparator(List<String> cells) {
        return !cells.isEmpty() && cells.stream().allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private String cell(List<String> cells, Integer index) {
        return index == null || index < 0 || index >= cells.size() ? "" : cells.get(index);
    }

    private String cleanCell(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("`") && cleaned.endsWith("`") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned.replace("\\|", "|");
    }

    private String normalize(String term) {
        return term.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private LearningAssistantException invalid(String message) {
        return LearningAssistantException.badRequest(
                LearningErrorCode.VOCABULARY_IMPORT_INVALID,
                message);
    }

    /**
     * 解析阶段的不可变词条。
     */
    public record ParsedVocabulary(
            int sourceOrder,
            String originalTerm,
            String normalizedTerm,
            String suggestedTerm,
            String phonetic,
            String definition,
            boolean suspicious,
            List<String> warnings) {
    }
}
