package com.chandler.fcc.server.starter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("验证数据源连通性及 chandler26_fcc 数据库 34 张表完整性")
    void testDatabaseConnectivityAndTables() throws Exception {
        assertNotNull(dataSource, "DataSource 数据源不能为空");

        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection, "数据库连接不可为空");
            assertFalse(connection.isClosed(), "数据库连接必须处于开启状态");

            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            assertEquals("chandler26_fcc", catalog, "当前连接的目标数据库必须是 chandler26_fcc");

            // 查询该库下所有数据表
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(catalog, null, "fcc_%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }

            // 断言 34 张 fcc_* 数据表已完整导入 (含账号体系新增的 fcc_admin_user)
            assertEquals(34, tables.size(), "fcc 表总数必须为 34 张");
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
