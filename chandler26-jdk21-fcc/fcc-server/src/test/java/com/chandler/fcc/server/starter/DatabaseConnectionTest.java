package com.chandler.fcc.server.starter;

import com.chandler.fcc.server.FccServerApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库连通性与 DDL 数据表验证测试
 *
 * @author Chandler
 */
@SpringBootTest(classes = FccServerApplication.class)
@DirtiesContext
class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("验证配置的数据源及核心业务表完整性")
    void testDatabaseConnectivityAndTables() throws Exception {
        assertNotNull(dataSource, "DataSource 数据源不能为空");

        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "数据库连接不可为空");
            assertFalse(connection.isClosed(), "数据库连接必须处于开启状态");

            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            assertNotNull(catalog, "必须连接到配置的业务数据库");

            // 查询该库下所有数据表
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(catalog, null, "fcc_%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }

            // 校验业务契约，不将新增业务表错误地视为数据库损坏。
            assertTrue(tables.contains("fcc_screen_pop_delivery"), "必须包含弹屏投递事实表");
            assertTrue(tables.contains("fcc_callback_task"), "必须包含回拨任务表");
            assertTrue(tables.contains("fcc_call_session"), "必须包含 fcc_call_session 表");
            assertTrue(tables.contains("fcc_call_leg"), "必须包含 fcc_call_leg 表");
            assertTrue(tables.contains("fcc_call_bridge"), "必须包含 fcc_call_bridge 表");
            assertTrue(tables.contains("fcc_call_event"), "必须包含 fcc_call_event 表");
            assertTrue(tables.contains("fcc_call_command"), "必须包含 fcc_call_command 表");
            assertTrue(tables.contains("fcc_flow_instance"), "必须包含 fcc_flow_instance 表");
            assertTrue(tables.contains("fcc_agent"), "必须包含 fcc_agent 表");
            assertTrue(tables.contains("fcc_admin_user"), "必须包含 fcc_admin_user 控制台账号表");
            assertTrue(tables.contains("fcc_system_config"), "必须包含 fcc_system_config 表");
            assertTrue(tables.contains("fcc_extension"), "必须包含 fcc_extension 表");
        }
    }

    @Test
    @DisplayName("回填历史通话会话的 flow_code 字段并验证")
    void testBackfillFlowCode() throws Exception {
        assertNotNull(dataSource);
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            // 1. 通过关联 fcc_flow_instance 和 fcc_flow_definition 权威回填
            int updated = stmt.executeUpdate(
                "UPDATE fcc_call_session s " +
                "JOIN fcc_flow_instance i ON s.id = i.call_id " +
                "JOIN fcc_flow_definition d ON d.id = i.flow_definition_id " +
                "SET s.flow_code = d.flow_key " +
                "WHERE s.flow_code IS NULL"
            );
            System.out.println("JOIN 更新 fcc_call_session.flow_code 条数: " + updated);

            // 2. 兜底更新
            stmt.executeUpdate("UPDATE fcc_call_session SET flow_code = 'SYSTEM_AGENT_FIRST' WHERE model_type = 'OUTBOUND_TWO_WAY_CALL' AND flow_code IS NULL");
            stmt.executeUpdate("UPDATE fcc_call_session SET flow_code = 'SYSTEM_PHONE_BINDING' WHERE model_type = 'PHONE_BINDING' AND flow_code IS NULL");
            stmt.executeUpdate("UPDATE fcc_call_session SET flow_code = 'SYSTEM_NOTIFICATION' WHERE model_type = 'AUTO_DIAL_NOTIFICATION' AND flow_code IS NULL");
            stmt.executeUpdate("UPDATE fcc_call_session SET flow_code = 'INBOUND_IVR' WHERE (model_type = 'INBOUND_CUSTOMER_SERVICE' OR direction = 'INBOUND') AND flow_code IS NULL");

            // 3. 验证是否有未回填 flow_code 的记录
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM fcc_call_session WHERE flow_code IS NULL")) {
                assertTrue(rs.next());
                int nullCount = rs.getInt(1);
                System.out.println("剩余 flow_code 为空的记录数: " + nullCount);
                assertEquals(0, nullCount, "所有历史通话记录的 flow_code 必须全部回填完成");
            }
        }
    }
}
