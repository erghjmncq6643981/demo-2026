package com.chandler.fcc.server.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.agent.infrastructure.PhoneBindingMapper;
import com.chandler.fcc.server.call.infrastructure.AfterCallMapper;
import com.chandler.fcc.server.call.infrastructure.CallbackRuntimeMapper;
import com.chandler.fcc.server.customer.api.CustomerRecord;
import com.chandler.fcc.server.customer.infrastructure.CustomerMapper;
import com.chandler.fcc.server.event.infrastructure.EventInboxMapper;
import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import com.chandler.fcc.server.websocket.persistence.ScreenPopDeliveryMapper;
import java.sql.Timestamp;
import java.util.Map;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 在一次性 MySQL 8 数据库上验证运行 Mapper 的真实 SQL 和单呼叫中心边界。
 */
@EnabledIfEnvironmentVariable(
    named = "FCC_SQL_TEST_URL",
    matches = "jdbc:mysql://127\\.0\\.0\\.1:13316/fcc_verify.*"
)
class RuntimeSqlIntegrationTest {

    /**
     * 验证话机绑定、坐席占用、客户资料、自动外呼和话后流程 SQL。
     *
     * @throws Exception MyBatis Mapper 初始化失败
     */
    @Test
    void validatesRuntimeMappersOnMySql() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
            System.getenv("FCC_SQL_TEST_URL"),
            "root",
            ""
        );
        SqlSessionTemplate session = createSession(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate transaction = new TransactionTemplate(
            new DataSourceTransactionManager(dataSource)
        );

        transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            seedResources(jdbc);
            verifyBindingAndPresence(session);
            verifyCustomerAndDialJob(session, jdbc);
            verifyScreenPopAndAfterCall(session, jdbc);
            verifyCallbackAndInbox(session);
        });
    }

    /**
     * 创建包含全部运行 Mapper XML 的独立 MyBatis 会话。
     *
     * @param dataSource 一次性测试数据源
     * @return SQL 会话模板
     * @throws Exception SqlSessionFactory 创建失败
     */
    private SqlSessionTemplate createSession(DriverManagerDataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);

        PathMatchingResourcePatternResolver resources = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(
            resources.getResource("classpath:mapper/AgentRuntimeMapper.xml"),
            resources.getResource("classpath:mapper/PhoneBindingMapper.xml"),
            resources.getResource("classpath:mapper/CustomerMapper.xml"),
            resources.getResource("classpath:mapper/DialJobMapper.xml"),
            resources.getResource("classpath:mapper/EventInboxMapper.xml"),
            resources.getResource("classpath:mapper/ScreenPopDeliveryMapper.xml"),
            resources.getResource("classpath:mapper/AfterCallMapper.xml"),
            resources.getResource("classpath:mapper/CallbackRuntimeMapper.xml")
        );
        return new SqlSessionTemplate(factory.getObject());
    }

    /**
     * 写入当前测试所需的最小节点、坐席和分机事实。
     *
     * @param jdbc 数据库访问器
     */
    private void seedResources(JdbcTemplate jdbc) {
        jdbc.update(
            "INSERT INTO fcc_telephony_node(id,node_id,status) VALUES(900000,'test-node','ONLINE')"
        );
        jdbc.update(
            "INSERT INTO fcc_agent(id,work_no,agent_name,status) VALUES(900001,'test-agent','test','ENABLED')"
        );
        jdbc.update(
            "INSERT INTO fcc_extension(id,extension,status) VALUES(900002,'1001','ENABLED')"
        );
    }

    /**
     * 验证拨号绑定和坐席原子占用 SQL。
     *
     * @param session SQL 会话
     */
    private void verifyBindingAndPresence(SqlSessionTemplate session) {
        PhoneBindingMapper bindings = session.getMapper(PhoneBindingMapper.class);
        assertNotNull(bindings.bindingContext("1001"));
        assertNotNull(bindings.lockBindingTarget("1001", "test-agent"));
        assertEquals(0, bindings.busy("test-agent", "1001"));
        bindings.disableBindings("test-agent", "1001");
        bindings.clearExtensions("test-agent", "1001");
        assertEquals(1, bindings.bindExtension("test-agent", "1001"));
        assertEquals(1, bindings.appendBinding(900003, "test-agent", "1001"));

        AgentRuntimeMapper runtime = session.getMapper(AgentRuntimeMapper.class);
        runtime.ensurePresence("test-agent");
        runtime.setPresence("test-agent", "READY");
        assertEquals(1, runtime.reserve("test-agent", "900010"));
        assertEquals(0, runtime.reserve("test-agent", "900011"));
        assertEquals(1, bindings.busy("test-agent", "1001"));
        assertEquals(0, runtime.release("test-agent", "900011"));
        assertEquals(1, runtime.release("test-agent", "900010"));
        assertEquals("ACW", runtime.presence("test-agent").get("status"));
    }

    /**
     * 验证客户乐观锁和外呼任务归属 SQL。
     *
     * @param session SQL 会话
     * @param jdbc 数据库访问器
     */
    private void verifyCustomerAndDialJob(SqlSessionTemplate session, JdbcTemplate jdbc) {
        CustomerMapper customers = session.getMapper(CustomerMapper.class);
        CustomerRecord customer = new CustomerRecord();
        customer.setId("900004");
        customer.setName("测试客户");
        customer.setPhoneNumber("1001");
        customer.setNotes("detail-only");
        customer.setVersion(0L);
        assertEquals(1, customers.insert("test-agent", customer));
        assertNull(customers.detail("another-agent", "900004"));
        assertNull(customers.list("test-agent", null, 0, 50).getFirst().getNotes());
        assertEquals(1, customers.update("test-agent", customer));
        assertEquals(0, customers.update("test-agent", customer));

        DialJobMapper jobs = session.getMapper(DialJobMapper.class);
        assertEquals(
            1,
            jobs.create(
                Map.of(
                    "id", "900005",
                    "owner", "test-agent",
                    "key", "test-job-1",
                    "mode", "PROGRESSIVE",
                    "maxAttempts", 2,
                    "payload", "{\"number\":\"1001\"}"
                )
            )
        );
        assertEquals(1L, jobs.schedulerLock());
        assertNull(jobs.next(), "整理态坐席的任务必须等待");
        session.getMapper(AgentRuntimeMapper.class).setPresence("test-agent", "READY");
        assertEquals("900005", jobs.next().get("id"));
        assertEquals(1, jobs.claim("900005"));
        assertEquals(0, jobs.claim("900005"));
        assertEquals(
            1,
            jobs.startAttempt(
                Map.of(
                    "attempt", "900006",
                    "id", "900005",
                    "attemptNo", 1,
                    "number", "1001"
                )
            )
        );
        assertEquals(1, jobs.attach("900006", "900010"));
        assertEquals(1, jobs.finishAttempt("900006", "FAILED", "NO_ANSWER"));
        assertEquals(1, jobs.finishJob("900005", "PENDING"));
        assertEquals(1, jobs.control("test-agent", "900005", "PAUSE"));
        assertNull(jobs.detail("another-agent", "900005"));
        assertEquals(1, jobs.frequency("1001"));

        jdbc.update(
            "INSERT INTO fcc_call_session(id,biz_id,ctrl_id,model_type,direction,status,started_at,primary_work_no) "
                + "VALUES(900010,'900006','test-call','OUTBOUND_TWO_WAY_CALL','OUTBOUND','CALLING',UTC_TIMESTAMP(3),'test-agent')"
        );
    }

    /**
     * 验证弹屏投递和一次性话后小结 SQL。
     *
     * @param session SQL 会话
     * @param jdbc 数据库访问器
     */
    private void verifyScreenPopAndAfterCall(SqlSessionTemplate session, JdbcTemplate jdbc) {
        ScreenPopDeliveryMapper deliveries = session.getMapper(ScreenPopDeliveryMapper.class);
        assertEquals(
            1,
            deliveries.save(
                "test-agent",
                "900010",
                "{\"callId\":\"900010\"}",
                System.currentTimeMillis() + 30_000
            )
        );
        assertEquals(0, deliveries.save("test-agent", "900010", "{}", System.currentTimeMillis()));
        assertEquals(1, deliveries.pending("test-agent").size());
        assertTrue(deliveries.pending("another-agent").isEmpty());
        deliveries.receipt("test-agent", "900010", "ACTIVATED");
        assertNotNull(
            jdbc.queryForObject(
                "SELECT activated_at FROM fcc_screen_pop_delivery WHERE call_id=900010",
                Timestamp.class
            )
        );
        deliveries.close("test-agent", "900010");
        assertTrue(deliveries.pending("test-agent").isEmpty());

        jdbc.update(
            "UPDATE fcc_call_session SET status='NORMAL_END',ended_at=UTC_TIMESTAMP(3) WHERE id=900010"
        );
        AfterCallMapper summaries = session.getMapper(AfterCallMapper.class);
        assertNull(summaries.lockEnded("another-agent", "900010"));
        assertEquals("900010", summaries.lockEnded("test-agent", "900010"));
        assertEquals(1, summaries.save("test-agent", "900010", "{\"notes\":\"saved\"}"));
        assertEquals(0, summaries.save("test-agent", "900010", "{\"notes\":\"changed\"}"));
        assertTrue(summaries.detail("test-agent", "900010").contains("saved"));
    }

    /**
     * 验证回拨归属与事件收件箱幂等 SQL。
     *
     * @param session SQL 会话
     */
    private void verifyCallbackAndInbox(SqlSessionTemplate session) {
        AgentRuntimeMapper runtime = session.getMapper(AgentRuntimeMapper.class);
        runtime.callback(900020, "900010", "1001", "1002", "NO_ANSWER");
        CallbackRuntimeMapper callbacks = session.getMapper(CallbackRuntimeMapper.class);
        assertEquals(1, callbacks.count("test-agent", null));
        assertNotNull(callbacks.lock("test-agent", "900020"));
        assertEquals(1, callbacks.schedule("test-agent", "900020", "900005"));
        assertNull(callbacks.lock("another-agent", "900020"));

        EventInboxMapper inbox = session.getMapper(EventInboxMapper.class);
        String eventId = "b".repeat(64);
        assertEquals(1, inbox.receive(eventId, "test-node", "{}"));
        assertEquals(0, inbox.receive(eventId, "test-node", "{}"));
        inbox.finish(eventId, "PROCESSED", null);
        assertEquals("PROCESSED", inbox.status(eventId));
    }
}
