package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("V112 词汇形态变形别名表迁移单测")
class V112__VocabularyMorphAliasTest {

    @Test
    @DisplayName("当表不存在时执行建表语句")
    void shouldCreateTableWhenNotExists() throws Exception {
        Context context = Mockito.mock(Context.class);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Statement statement = Mockito.mock(Statement.class);
        ResultSet tablesRs = Mockito.mock(ResultSet.class);

        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getCatalog()).thenReturn("test_db");

        when(metaData.getTables(eq("test_db"), isNull(), eq("learning_vocabulary_alias"), any())).thenReturn(tablesRs);
        when(tablesRs.next()).thenReturn(false);

        V112__VocabularyMorphAlias migration = new V112__VocabularyMorphAlias();
        migration.migrate(context);

        verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS learning_vocabulary_alias"));
    }

    @Test
    @DisplayName("当表已存在时跳过建表")
    void shouldSkipWhenTableExists() throws Exception {
        Context context = Mockito.mock(Context.class);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Statement statement = Mockito.mock(Statement.class);
        ResultSet tablesRs = Mockito.mock(ResultSet.class);

        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getCatalog()).thenReturn("test_db");

        when(metaData.getTables(eq("test_db"), isNull(), eq("learning_vocabulary_alias"), any())).thenReturn(tablesRs);
        when(tablesRs.next()).thenReturn(true);

        V112__VocabularyMorphAlias migration = new V112__VocabularyMorphAlias();
        migration.migrate(context);

        verify(statement, never()).execute(anyString());
    }
}
