package com.chandler.learning.agent.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 校验后端对外对象、领域对象和公共业务方法的中文说明质量。 */
class ChineseDocumentationGovernanceTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*private\\s+(?!static\\s+final)[\\w<>?,.\\[\\] ]+\\s+(\\w+)\\s*(?:=|;)");
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^\\s*(public|protected|private)\\s+(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?"
                    + "(?!class\\b|record\\b|interface\\b|enum\\b)[\\w<>?,.\\[\\] ]+\\s+(\\w+)\\s*\\(");
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u3400-\\u9fff]");
    private static final Pattern SCHEMA_DESCRIPTION_PATTERN = Pattern.compile(
            "@Schema\\s*\\([^)]*description\\s*=\\s*\"([^\"]+)\"[^)]*\\)", Pattern.DOTALL);
    private static final Set<String> VAGUE_SCHEMA_DESCRIPTIONS = Set.of(
            "名称", "编码", "状态", "类型", "内容", "数量", "时间", "业务属性", "主键标识",
            "关联业务标识", "是否已解析", "列表数据", "总数量", "业务状态");

    @Test
    void apiDtoFieldsHaveSpecificChineseSchemaDescriptions() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String normalized = normalize(file);
            if (!normalized.contains("/api/request/") && !normalized.contains("/api/response/")) {
                continue;
            }
            inspectFields(file, true, violations);
        }
        assertNoViolations("API DTO 字段缺少明确的中文 @Schema", violations);
    }

    @Test
    void entityAndBusinessObjectFieldsHaveChineseDescriptions() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String normalized = normalize(file);
            if (!normalized.contains("/domain/entity/") && !normalized.contains("/domain/bo/")) {
                continue;
            }
            inspectFields(file, false, violations);
        }
        assertNoViolations("实体或 BO 字段缺少中文说明", violations);
    }

    @Test
    void publicBusinessAndTechnicalBoundaryMethodsHaveChineseJavaDoc() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : javaFiles()) {
            String normalized = normalize(file);
            if (!isDocumentedBoundaryPath(normalized)) {
                continue;
            }
            inspectPublicMethods(file, violations);
        }
        assertNoViolations("公共业务或技术边界方法缺少中文 JavaDoc", violations);
    }

    @Test
    void generatedPlaceholderCommentsAreForbidden() throws IOException {
        List<String> violations = new ArrayList<>();
        Pattern placeholder = Pattern.compile(
                "(?:处理|创建或保存|查询|转换|判断|执行|构建|返回|更新) \\{@code \\w+} 相关业务。"
                        + "|\\b[A-Za-z][A-Za-z0-9]* (?:类|属性|字段)。|业务边界。");
        for (Path file : javaFiles()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                if (placeholder.matcher(lines.get(index)).find()) {
                    violations.add(location(file, index + 1, lines.get(index).trim()));
                }
            }
        }
        assertNoViolations("禁止无业务含义的批量占位注释", violations);
    }

    private void inspectFields(Path file, boolean requireSchema, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int boundary = classDeclarationLine(lines);
        for (int index = Math.max(0, boundary + 1); index < lines.size(); index++) {
            Matcher fieldMatcher = FIELD_PATTERN.matcher(lines.get(index));
            if (!fieldMatcher.find()) {
                continue;
            }
            String context = String.join("\n", lines.subList(Math.max(0, boundary + 1), index));
            Matcher schemaMatcher = SCHEMA_DESCRIPTION_PATTERN.matcher(context);
            String schemaDescription = null;
            while (schemaMatcher.find()) {
                schemaDescription = schemaMatcher.group(1).trim();
            }
            boolean hasChineseJavaDoc = hasChineseJavaDoc(context);
            if (requireSchema) {
                if (schemaDescription == null || !containsChinese(schemaDescription)
                        || VAGUE_SCHEMA_DESCRIPTIONS.contains(schemaDescription)) {
                    violations.add(location(file, index + 1, fieldMatcher.group(1)));
                }
            } else if (!hasChineseJavaDoc && (schemaDescription == null || !containsChinese(schemaDescription))) {
                violations.add(location(file, index + 1, fieldMatcher.group(1)));
            }
            boundary = index;
        }
    }

    private void inspectPublicMethods(Path file, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int boundary = classDeclarationLine(lines);
        for (int index = Math.max(0, boundary + 1); index < lines.size(); index++) {
            Matcher matcher = METHOD_PATTERN.matcher(lines.get(index));
            if (!matcher.find()) {
                continue;
            }
            if ("public".equals(matcher.group(1))) {
                String context = String.join("\n", lines.subList(Math.max(0, boundary + 1), index));
                if (!hasChineseJavaDoc(context)) {
                    violations.add(location(file, index + 1, matcher.group(2)));
                }
            }
            boundary = index;
        }
    }

    private boolean hasChineseJavaDoc(String context) {
        int start = context.lastIndexOf("/**");
        int end = context.lastIndexOf("*/");
        return start >= 0 && end > start && containsChinese(context.substring(start, end + 2));
    }

    private boolean containsChinese(String value) {
        return CHINESE_PATTERN.matcher(value).find();
    }

    private int classDeclarationLine(List<String> lines) {
        Pattern declaration = Pattern.compile("\\b(?:class|record|interface|enum)\\s+\\w+");
        for (int index = 0; index < lines.size(); index++) {
            if (declaration.matcher(lines.get(index)).find()) {
                return index;
            }
        }
        return -1;
    }

    private List<Path> javaFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private boolean isDocumentedBoundaryPath(String normalized) {
        return normalized.contains("/application/")
                || normalized.contains("/api/controller/")
                || normalized.contains("/security/")
                || normalized.contains("/ai/gateway/")
                || normalized.contains("/config/")
                || normalized.contains("/common/web/")
                || normalized.contains("/exception/");
    }

    private String location(Path file, int line, String subject) {
        return normalize(file) + ":" + line + " -> " + subject;
    }

    private void assertNoViolations(String title, List<String> violations) {
        assertTrue(violations.isEmpty(), () -> title + "，共 " + violations.size() + " 处：\n"
                + String.join("\n", violations));
    }
}
