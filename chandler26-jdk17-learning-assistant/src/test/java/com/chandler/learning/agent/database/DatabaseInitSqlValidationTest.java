package com.chandler.learning.agent.database;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验项目数据库初始化 SQL 的完整性、幂等性与模块/一键合并脚本一致性（不依赖 Docker）。
 */
class DatabaseInitSqlValidationTest {

    private static final Pattern TABLE_NAME_ANNOTATION = Pattern.compile("@TableName\\(\"([a-z0-9_]+)\"\\)");
    private static final Pattern CREATE_TABLE_IDEMPOTENT = Pattern.compile("CREATE TABLE IF NOT EXISTS\\s+([a-z0-9_]+)");
    private static final Pattern BARE_CREATE_TABLE = Pattern.compile("CREATE TABLE\\s+(?!IF NOT EXISTS)");
    private static final List<String> MODULAR_SCRIPTS = List.of(
            "db/schema/00_ai_schema_mysql.sql",
            "db/schema/10_learning_core_schema_mysql.sql",
            "db/schema/20_vocabulary_plan_schema_mysql.sql",
            "db/schema/30_rumination_schema_mysql.sql",
            "db/init/00_ai_agent_seed_mysql.sql",
            "db/init/01_system_admin_seed_mysql.sql"
    );

    @Test
    void coversAllJavaEntityTablesInBothModularSchemasAndUnifiedInitScript() throws Exception {
        Set<String> entityTables = scanJavaEntityTables();
        assertThat(entityTables).isNotEmpty();

        Set<String> modularTables = new TreeSet<>();
        for (String script : MODULAR_SCRIPTS) {
            String sql = readClasspathResource(script);
            assertThat(BARE_CREATE_TABLE.matcher(sql).find())
                    .as("脚本 %s 中禁止出现非幂等的 CREATE TABLE（必须使用 IF NOT EXISTS）", script)
                    .isFalse();
            assertThat(sql).as("初始化脚本 %s 不应包含增量 ALTER TABLE 语句", script)
                    .doesNotContain("ALTER TABLE");
            Matcher matcher = CREATE_TABLE_IDEMPOTENT.matcher(sql);
            while (matcher.find()) {
                modularTables.add(matcher.group(1));
            }
        }

        String unifiedSql = readClasspathResource("db/init_all_mysql.sql");
        assertThat(BARE_CREATE_TABLE.matcher(unifiedSql).find()).isFalse();
        assertThat(unifiedSql).doesNotContain("ALTER TABLE");

        Set<String> unifiedTables = new TreeSet<>();
        Matcher unifiedMatcher = CREATE_TABLE_IDEMPOTENT.matcher(unifiedSql);
        while (unifiedMatcher.find()) {
            unifiedTables.add(unifiedMatcher.group(1));
        }

        assertThat(modularTables).containsExactlyElementsOf(entityTables);
        assertThat(unifiedTables).containsExactlyElementsOf(entityTables);
    }

    @Test
    void unifiedInitScriptStaysStrictlyInSyncWithModularScripts() throws Exception {
        String unifiedSql = readClasspathResource("db/init_all_mysql.sql");
        for (String script : MODULAR_SCRIPTS) {
            String content = readClasspathResource(script).trim();
            assertThat(unifiedSql)
                    .as("一键初始化脚本 db/init_all_mysql.sql 必须与 %s 保持严格同步", script)
                    .contains(content);
        }
        assertThat(unifiedSql)
                .contains("role_code VARCHAR(20) NOT NULL DEFAULT 'USER'")
                .contains("idx_learning_user_role_enabled");
    }

    private Set<String> scanJavaEntityTables() throws Exception {
        Set<String> tables = new TreeSet<>();
        Path javaRoot = Path.of("src/main/java");
        try (var stream = Files.walk(javaRoot)) {
            for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = TABLE_NAME_ANNOTATION.matcher(source);
                while (matcher.find()) {
                    tables.add(matcher.group(1));
                }
            }
        }
        return tables;
    }

    private String readClasspathResource(String location) throws Exception {
        return new ClassPathResource(location).getContentAsString(StandardCharsets.UTF_8);
    }
}
