package db.migration;

import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V111__DropAiForeignKeysTest {

    @Test
    @DisplayName("当外键存在时执行 DROP FOREIGN KEY")
    void shouldDropForeignKeysWhenExist() throws Exception {
        Context context = Mockito.mock(Context.class);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Statement statement = Mockito.mock(Statement.class);

        ResultSet agentFkRs = Mockito.mock(ResultSet.class);
        when(agentFkRs.next()).thenReturn(true, false);
        when(agentFkRs.getString("FK_NAME")).thenReturn("fk_ai_agent_model_config");

        ResultSet messageFkRs = Mockito.mock(ResultSet.class);
        when(messageFkRs.next()).thenReturn(true, false);
        when(messageFkRs.getString("FK_NAME")).thenReturn("fk_ai_chat_message_session");

        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getCatalog()).thenReturn("test_db");

        when(metaData.getImportedKeys(eq("test_db"), isNull(), eq("ai_agent"))).thenReturn(agentFkRs);
        when(metaData.getImportedKeys(eq("test_db"), isNull(), eq("ai_chat_message"))).thenReturn(messageFkRs);

        V111__DropAiForeignKeys migration = new V111__DropAiForeignKeys();
        migration.migrate(context);

        verify(statement).execute("ALTER TABLE ai_agent DROP FOREIGN KEY fk_ai_agent_model_config");
        verify(statement).execute("ALTER TABLE ai_chat_message DROP FOREIGN KEY fk_ai_chat_message_session");
    }

    @Test
    @DisplayName("当外键不存在时跳过 DROP")
    void shouldSkipWhenForeignKeysDoNotExist() throws Exception {
        Context context = Mockito.mock(Context.class);
        Connection connection = Mockito.mock(Connection.class);
        DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);
        Statement statement = Mockito.mock(Statement.class);

        ResultSet emptyRs1 = Mockito.mock(ResultSet.class);
        when(emptyRs1.next()).thenReturn(false);

        ResultSet emptyRs2 = Mockito.mock(ResultSet.class);
        when(emptyRs2.next()).thenReturn(false);

        when(context.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.getCatalog()).thenReturn("test_db");

        when(metaData.getImportedKeys(eq("test_db"), isNull(), eq("ai_agent"))).thenReturn(emptyRs1);
        when(metaData.getImportedKeys(eq("test_db"), isNull(), eq("ai_chat_message"))).thenReturn(emptyRs2);

        V111__DropAiForeignKeys migration = new V111__DropAiForeignKeys();
        migration.migrate(context);

        verify(statement, never()).execute(anyString());
    }
}
