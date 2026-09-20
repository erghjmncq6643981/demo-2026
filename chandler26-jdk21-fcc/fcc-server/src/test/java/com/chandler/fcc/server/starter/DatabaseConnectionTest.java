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
}
