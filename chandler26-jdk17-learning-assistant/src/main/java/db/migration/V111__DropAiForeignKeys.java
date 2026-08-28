package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 去除 AI 模块物理外键约束，转为由代码和事务逻辑保证数据完整性，消除跨表锁竞争与软删除冲突。
 */
public class V111__DropAiForeignKeys extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        dropForeignKeyIfExists(connection, "ai_agent", "fk_ai_agent_model_config");
        dropForeignKeyIfExists(connection, "ai_chat_message", "fk_ai_chat_message_session");
    }

    private void dropForeignKeyIfExists(Connection connection, String table, String constraintName) throws SQLException {
        if (hasForeignKey(connection, table, constraintName)) {
            execute(connection, "ALTER TABLE " + table + " DROP FOREIGN KEY " + constraintName);
        }
    }

    private boolean hasForeignKey(Connection connection, String table, String constraintName) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getImportedKeys(connection.getCatalog(), null, table)) {
            while (result.next()) {
                if (constraintName.equalsIgnoreCase(result.getString("FK_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
