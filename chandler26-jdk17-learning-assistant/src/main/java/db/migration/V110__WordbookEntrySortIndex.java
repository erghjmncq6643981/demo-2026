package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** 为个人单词本词条列表添加针对复习时间和创建时间的复合排序索引，消除分页 Filesort。 */
public class V110__WordbookEntrySortIndex extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        addIndex(connection, "learning_wordbook_entry", "idx_learning_wordbook_entry_wb_due_order",
                "(user_id, wordbook_id, deleted, next_review_time, create_time)");
    }

    private void addIndex(Connection connection, String table, String index, String columns) throws SQLException {
        if (!hasIndex(connection, table, index)) {
            execute(connection, "ALTER TABLE " + table + " ADD KEY " + index + " " + columns);
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
