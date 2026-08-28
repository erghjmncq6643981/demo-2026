package com.chandler.learning.agent.database;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseSchemaIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("study")
            .withUsername("learning")
            .withPassword("learning-test");

    @Test
    void initializesCurrentSchemaAndSeedDataOnRealMysql() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            for (String script : List.of(
                    "db/schema/00_ai_schema_mysql.sql",
                    "db/schema/10_learning_core_schema_mysql.sql",
                    "db/schema/20_vocabulary_plan_schema_mysql.sql",
                    "db/schema/30_article_reading_schema_mysql.sql",
                    "db/schema/40_user_authorization_schema_mysql.sql",
                    "db/init/00_ai_agent_seed_mysql.sql",
                    "db/init/01_system_admin_seed_mysql.sql")) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(script));
            }

            assertThat(tableExists(connection, "learning_plan")).isTrue();
            assertThat(tableExists(connection, "ai_model_call_record")).isTrue();
            assertThat(tableExists(connection, "learning_system_log_outbox")).isTrue();
            assertThat(queryCount(connection, "SELECT COUNT(*) FROM ai_agent")).isGreaterThan(0);
            assertThat(queryCount(connection, "SELECT COUNT(*) FROM learning_user WHERE role_code = 'ADMIN'"))
                    .isGreaterThan(0);
        }
    }

    private boolean tableExists(java.sql.Connection connection, String tableName) throws Exception {
        try (var statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = ?
                """)) {
            statement.setString(1, tableName);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private int queryCount(java.sql.Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }
}
