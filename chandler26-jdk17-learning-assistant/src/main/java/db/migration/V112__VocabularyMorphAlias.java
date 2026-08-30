package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 创建英语词汇形态变形与别名索引表，支持名词复数、动词时态与屈折形态的高性能容错与缓存命中。
 */
public class V112__VocabularyMorphAlias extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, "learning_vocabulary_alias")) {
            execute(connection, """
                CREATE TABLE IF NOT EXISTS learning_vocabulary_alias (
                    id BIGINT NOT NULL COMMENT '主键',
                    create_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人用户 ID',
                    update_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人用户 ID',
                    vocabulary_id BIGINT NOT NULL COMMENT '公共词卡缓存 ID',
                    alias_term VARCHAR(128) NOT NULL COMMENT '变形或别名单词',
                    normalized_alias VARCHAR(128) NOT NULL COMMENT '归一化别名',
                    lemma VARCHAR(128) NOT NULL COMMENT '原形单词',
                    normalized_lemma VARCHAR(128) NOT NULL COMMENT '归一化原形',
                    alias_type VARCHAR(50) NOT NULL COMMENT '别名类型',
                    source VARCHAR(50) NOT NULL DEFAULT 'rule' COMMENT '来源',
                    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否逻辑删除',
                    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
                    PRIMARY KEY (id),
                    UNIQUE KEY uk_learning_vocabulary_alias (normalized_alias, vocabulary_id),
                    KEY idx_learning_vocabulary_alias_lookup (normalized_alias, deleted),
                    KEY idx_learning_vocabulary_alias_vocab (vocabulary_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='英语词汇形态变形与别名索引'
            """);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet rs = metadata.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
