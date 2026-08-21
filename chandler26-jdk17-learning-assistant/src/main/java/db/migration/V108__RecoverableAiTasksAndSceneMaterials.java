package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 可恢复 AI 任务和场景材料版本升级。
 * <p>
 * 空库的 V1 已加载当前完整 schema，存量库则只有 107 结构；因此本迁移通过元数据判断后补齐，
 * 同时兼容两种启动路径。
 */
public class V108__RecoverableAiTasksAndSceneMaterials extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        upgradeTaskTable(connection);
        upgradeSceneMaterialTable(connection);
        upgradeSceneNoteIndex(connection);
        createTaskStepTable(connection);
        createTaskAttemptTable(connection);
        createRelatedWordTable(connection);
        upsertRelatedWordsPrompt(connection);
        tightenScenePrompt(connection);
    }

    private void upgradeTaskTable(Connection connection) throws SQLException {
        addColumn(connection, "learning_ai_async_task", "owner_user_id",
                "BIGINT NULL COMMENT '任务成果归属用户 ID' AFTER user_id");
        addColumn(connection, "learning_ai_async_task", "trigger_user_id",
                "BIGINT NULL COMMENT '任务触发用户 ID，系统触发时为空' AFTER owner_user_id");
        addColumn(connection, "learning_ai_async_task", "operator_user_id",
                "BIGINT NULL COMMENT '最近一次继续或干预的用户 ID' AFTER trigger_user_id");
        addColumn(connection, "learning_ai_async_task", "trigger_type",
                "VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '触发来源' AFTER operator_user_id");
        addColumn(connection, "learning_ai_async_task", "visibility",
                "VARCHAR(20) NOT NULL DEFAULT 'owner_admin' COMMENT '可见范围' AFTER trigger_type");
        addColumn(connection, "learning_ai_async_task", "business_type",
                "VARCHAR(50) NULL COMMENT '关联业务对象类型' AFTER related_job_id");
        addColumn(connection, "learning_ai_async_task", "business_id",
                "VARCHAR(100) NULL COMMENT '关联业务对象 ID' AFTER business_type");
        addColumn(connection, "learning_ai_async_task", "idempotency_key",
                "VARCHAR(160) NULL COMMENT '有效任务业务幂等键' AFTER business_id");
        execute(connection, "UPDATE learning_ai_async_task SET owner_user_id = user_id, "
                + "trigger_user_id = user_id, operator_user_id = user_id WHERE owner_user_id IS NULL");
        execute(connection, "ALTER TABLE learning_ai_async_task MODIFY owner_user_id BIGINT NOT NULL "
                + "COMMENT '任务成果归属用户 ID'");
        addIndex(connection, "learning_ai_async_task", "idx_learning_ai_task_owner_status",
                "(owner_user_id, status, deleted, update_time)");
        addIndex(connection, "learning_ai_async_task", "idx_learning_ai_task_idempotency",
                "(idempotency_key, status, deleted)");
    }

    private void upgradeSceneMaterialTable(Connection connection) throws SQLException {
        addColumn(connection, "learning_scene_material", "revision_no",
                "INT NOT NULL DEFAULT 1 COMMENT '单元内材料版本号' AFTER unit_id");
        addColumn(connection, "learning_scene_material", "material_status",
                "VARCHAR(20) NOT NULL DEFAULT 'published' COMMENT '材料状态' AFTER revision_no");
        addColumn(connection, "learning_scene_material", "current_version",
                "TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否当前版本' AFTER material_status");
        addColumn(connection, "learning_scene_material", "supersedes_material_id",
                "BIGINT NULL COMMENT '上一材料 ID' AFTER current_version");
        dropIndex(connection, "learning_scene_material", "uk_learning_scene_material_unit");
        addIndex(connection, "learning_scene_material", "uk_learning_scene_material_revision",
                "(unit_id, revision_no) UNIQUE");
        addIndex(connection, "learning_scene_material", "idx_learning_scene_material_current",
                "(unit_id, current_version, deleted)");
    }

    private void upgradeSceneNoteIndex(Connection connection) throws SQLException {
        dropIndex(connection, "learning_scene_material_note", "uk_learning_scene_material_note_user_unit");
        addIndex(connection, "learning_scene_material_note", "uk_learning_scene_material_note_user_material",
                "(user_id, scene_material_id, deleted) UNIQUE");
    }

    private void createTaskStepTable(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS learning_ai_async_task_step (
                    id BIGINT NOT NULL, create_by BIGINT NOT NULL DEFAULT 0, update_by BIGINT NOT NULL DEFAULT 0,
                    task_id BIGINT NOT NULL, step_code VARCHAR(60) NOT NULL, step_name VARCHAR(100) NOT NULL,
                    step_order INT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'pending',
                    completed_count INT NOT NULL DEFAULT 0, total_count INT NOT NULL DEFAULT 1,
                    checkpoint_json JSON DEFAULT NULL, lease_token VARCHAR(64) DEFAULT NULL,
                    lease_until DATETIME DEFAULT NULL, heartbeat_time DATETIME DEFAULT NULL,
                    attempt_count INT NOT NULL DEFAULT 0, max_attempt_count INT NOT NULL DEFAULT 3,
                    error_message VARCHAR(1000) DEFAULT NULL, started_time DATETIME DEFAULT NULL,
                    finished_time DATETIME DEFAULT NULL, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted TINYINT(1) NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id), UNIQUE KEY uk_ai_task_step (task_id, step_code),
                    KEY idx_ai_task_step_status (task_id, status, step_order, deleted),
                    KEY idx_ai_task_step_lease (status, lease_until, deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 异步任务可恢复步骤'
                """);
    }

    private void createTaskAttemptTable(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS learning_ai_async_task_attempt (
                    id BIGINT NOT NULL, create_by BIGINT NOT NULL DEFAULT 0, update_by BIGINT NOT NULL DEFAULT 0,
                    task_id BIGINT NOT NULL, step_id BIGINT NOT NULL, operator_user_id BIGINT DEFAULT NULL,
                    attempt_no INT NOT NULL, status VARCHAR(20) NOT NULL, model_config_id BIGINT DEFAULT NULL,
                    provider VARCHAR(50) DEFAULT NULL, model_name VARCHAR(100) DEFAULT NULL,
                    model_call_record_id BIGINT DEFAULT NULL, prompt_tokens INT DEFAULT NULL,
                    completion_tokens INT DEFAULT NULL, total_tokens INT DEFAULT NULL, cost_time BIGINT DEFAULT NULL,
                    error_code VARCHAR(80) DEFAULT NULL, error_message VARCHAR(1000) DEFAULT NULL,
                    started_time DATETIME NOT NULL, finished_time DATETIME DEFAULT NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted TINYINT(1) NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id), UNIQUE KEY uk_ai_task_step_attempt (step_id, attempt_no),
                    KEY idx_ai_task_attempt_task (task_id, create_time, deleted),
                    KEY idx_ai_task_attempt_model_call (model_call_record_id, deleted)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 任务步骤执行尝试审计'
                """);
    }

    private void createRelatedWordTable(Connection connection) throws SQLException {
        execute(connection, """
                CREATE TABLE IF NOT EXISTS learning_scene_related_word (
                    id BIGINT NOT NULL, create_by BIGINT NOT NULL DEFAULT 0, update_by BIGINT NOT NULL DEFAULT 0,
                    user_id BIGINT NOT NULL, plan_id BIGINT NOT NULL, unit_id BIGINT NOT NULL,
                    scene_material_id BIGINT NOT NULL, term VARCHAR(255) NOT NULL,
                    normalized_term VARCHAR(255) NOT NULL, phonetic VARCHAR(255) DEFAULT NULL,
                    meaning_text VARCHAR(1000) DEFAULT NULL, context_meaning VARCHAR(1000) DEFAULT NULL,
                    category_code VARCHAR(50) DEFAULT NULL, category_name VARCHAR(80) DEFAULT NULL,
                    source_type VARCHAR(20) NOT NULL DEFAULT 'ai', sort_order INT NOT NULL DEFAULT 0,
                    promoted TINYINT(1) NOT NULL DEFAULT 0, promoted_entry_id BIGINT DEFAULT NULL,
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    deleted TINYINT(1) NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (id), UNIQUE KEY uk_scene_related_material_term (scene_material_id, normalized_term),
                    KEY idx_scene_related_unit (unit_id, deleted, sort_order),
                    KEY idx_scene_related_plan (plan_id, deleted, update_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='具体材料版本的场景相关词汇'
                """);
    }

    private void upsertRelatedWordsPrompt(Connection connection) throws SQLException {
        String sql = """
                INSERT INTO ai_prompt_template
                (id, name, code, type, tags, content, variables, description, example_input, example_output,
                 public_template, enabled, sequence, create_by, create_time, update_by, update_time, deleted, version)
                VALUES (1204, ?, 'english_vocab_scene_related_words_json', 'user', ?, ?, CAST(? AS JSON), ?, ?, ?,
                        1, 1, 7, 0, CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0, 0)
                ON DUPLICATE KEY UPDATE content = VALUES(content), variables = VALUES(variables),
                                        description = VALUES(description), update_time = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "场景相关词汇 JSON");
            statement.setString(2, "英语,场景词汇,分类,JSON");
            statement.setString(3, "学习目的：{{learning_purpose}}。场景标题：{{scene_title}}。场景摘要：{{scene_summary}}。"
                    + "场景文章：{{learning_text}}。核心词：{{core_words}}。已经生成的场景相关词：{{existing_words}}。请生成 {{target_word_count}} 个帮助学习者"
                    + "扩展场景认知的英语词汇，覆盖具体事物、人物、地点和常用动作，不要重复核心词或已经生成的场景相关词。"
                    + "只输出合法 JSON，根字段为 related_words；每项包含 term、phonetic、meaning、"
                    + "context_meaning、category_code、category_name。");
            statement.setString(4, "[{\"name\":\"learning_purpose\",\"required\":true},"
                    + "{\"name\":\"scene_title\",\"required\":true},{\"name\":\"scene_summary\",\"required\":true},"
                    + "{\"name\":\"learning_text\",\"required\":true},{\"name\":\"core_words\",\"required\":true},"
                    + "{\"name\":\"existing_words\",\"required\":true},{\"name\":\"target_word_count\",\"required\":true}]");
            statement.setString(5, "为已生成材料独立补充分类明确的场景词汇");
            statement.setString(6, "{\"scene_title\":\"周末大扫除\",\"core_words\":[\"clean\"]}");
            statement.setString(7, "{\"related_words\":[]}");
            statement.executeUpdate();
        }
    }

    /** 存量库中的旧场景 Prompt 仍可能要求返回 supplementary，升级时收紧为独立相关词动作。 */
    private void tightenScenePrompt(Connection connection) throws SQLException {
        execute(connection, "UPDATE ai_prompt_template SET content = REPLACE(content, "
                + "'可添加场景常见具体名词作为 supplementary，但 supplementary 不得冒充词表词。', "
                + "'词汇数组只返回 core 和 review，场景扩展名词由独立动作生成，不要返回 supplementary 或其他额外词条。' "
                + "WHERE code = 'english_vocab_scene_unit_json'");
    }

    private void addColumn(Connection connection, String table, String column, String definition)
            throws SQLException {
        if (!hasColumn(connection, table, column)) {
            execute(connection, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void addIndex(Connection connection, String table, String index, String definition)
            throws SQLException {
        if (!hasIndex(connection, table, index)) {
            boolean unique = definition.endsWith(" UNIQUE");
            String columns = unique ? definition.substring(0, definition.length() - " UNIQUE".length()) : definition;
            execute(connection, "ALTER TABLE " + table + " ADD " + (unique ? "UNIQUE " : "")
                    + "INDEX " + index + " " + columns);
        }
    }

    private void dropIndex(Connection connection, String table, String index) throws SQLException {
        if (hasIndex(connection, table, index)) {
            execute(connection, "ALTER TABLE " + table + " DROP INDEX " + index);
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getColumns(connection.getCatalog(), null, table, column)) {
            return result.next();
        }
    }

    private boolean hasIndex(Connection connection, String table, String index) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (result.next()) {
                if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) return true;
            }
            return false;
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
