package com.chandler.fcc.server.call.application;

import com.chandler.fcc.server.agent.infrastructure.AgentParticipationMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.chandler.fcc.server.agent.application.AgentLegLifecycleService;
import com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper;
import com.chandler.fcc.server.call.domain.CallEndDecision;
import com.chandler.fcc.server.call.infrastructure.persistence.mapper.CallEndMapper;
import com.chandler.fcc.server.outbound.application.DialJobReconciliationService;
import com.chandler.fcc.server.outbound.infrastructure.DialJobMapper;
import java.time.LocalDateTime;
import java.util.List;
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

/** 在独立临时 MySQL 基线库验证真实 SQL、单坐席释放、终态幂等及关联修复。 */
@EnabledIfEnvironmentVariable(named = "FCC_SQL_TEST_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:13316/fcc_verify.*")
class CallEndSqlIntegrationTest {
    /**
     * 所有夹具与写入在回滚事务中执行，禁止指向业务库。
     * @throws Exception MyBatis 映射初始化失败
     */
    @Test
    void verifiesEndRepairAndIndependentAgentLegOnRealSql() throws Exception {
        var source = new DriverManagerDataSource(System.getenv("FCC_SQL_TEST_URL"), "root", "");
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        var config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(config);
        var resources = new PathMatchingResourcePatternResolver();
        factory.setMapperLocations(resources.getResource("classpath:mapper/CallEndMapper.xml"),
            resources.getResource("classpath:mapper/AgentRuntimeMapper.xml"),
            resources.getResource("classpath:mapper/DialJobMapper.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        var jdbc = new JdbcTemplate(source);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            seed(jdbc);
            CallEndMapper mapper = session.getMapper(CallEndMapper.class);
            AgentRuntimeMapper agents = session.getMapper(AgentRuntimeMapper.class);
            var finalization = new CallFinalizationService(mapper,
                new DialJobReconciliationService(session.getMapper(DialJobMapper.class)), mock(AgentLegLifecycleService.class),
                mock(AgentParticipationMapper.class));

            var candidates = mapper.candidates(0, LocalDateTime.of(2026, 10, 3, 0, 0), 100);
            assertEquals(2, candidates.size());
            assertEquals(9001L, mapper.resolveChannel("ctrl-test", "agent-a", "test-node").getId());
            assertNull(mapper.resolveChannel("ctrl-test", "unrelated", "test-node"));
            assertEquals(0, agents.releaseEndedLeg(9001L, "agent-a"));
            mapper.recordLegEnd(9001L, "agent-a", "test-node", LocalDateTime.of(2026, 10, 1, 8, 0), "NORMAL_CLEARING");
            jdbc.update("INSERT INTO fcc_call_leg(id,call_id,channel_uuid,node_id,role_type,direction,state,created_time,metadata) "
                + "VALUES(9204,9001,'agent-a-replacement','test-node','AGENT','OUTBOUND','BRIDGE',UTC_TIMESTAMP(3),JSON_OBJECT('agentWorkNo','agent-a-work'))");
            assertEquals(0, agents.releaseEndedLeg(9001L, "agent-a"));
            assertEquals(0, agents.release("agent-a-work", "9001"));
            jdbc.update("UPDATE fcc_call_session SET status='NORMAL_END',ended_at=UTC_TIMESTAMP(3) WHERE id=9001");
            assertEquals(0, mapper.releaseAgents(List.of(9001L)));
            jdbc.update("UPDATE fcc_call_session SET status='CONNECTED',ended_at=NULL WHERE id=9001");
            assertEquals("ANSWERED", agents.presence("agent-a-work").getWorkStatus());
            mapper.recordLegEnd(9001L, "agent-a-replacement", "test-node", LocalDateTime.of(2026, 10, 1, 8, 1), "NORMAL_CLEARING");
            jdbc.update("INSERT INTO fcc_call_leg(id,call_id,channel_uuid,node_id,role_type,direction,state,created_time) "
                + "VALUES(9205,9001,'unknown-agent','test-node','AGENT','OUTBOUND','BRIDGE',UTC_TIMESTAMP(3))");
            assertEquals(0, agents.releaseEndedLeg(9001L, "agent-a"));
            mapper.recordLegEnd(9001L, "unknown-agent", "test-node", LocalDateTime.of(2026, 10, 1, 8, 2), "NORMAL_CLEARING");
            assertEquals(1, agents.releaseEndedLeg(9001L, "agent-a"));
            assertEquals("READY", agents.presence("agent-a-work").getWorkStatus());
            assertNull(agents.presence("agent-a-work").getActiveCallId());
            assertEquals("ANSWERED", agents.presence("agent-b-work").getWorkStatus());
            assertEquals("9001", agents.presence("agent-b-work").getActiveCallId());
            assertEquals(0, agents.releaseEndedLeg(9001L, "agent-a"));
            assertEquals("CONNECTED", jdbc.queryForObject("SELECT status FROM fcc_call_session WHERE id=9001", String.class));

            // A 已接另一通通话，旧 Leg/Call 重投均不能释放 A 的新占用。
            jdbc.update("UPDATE fcc_agent_presence SET active_call_id=9002,work_status='ANSWERED' WHERE agent_id=9101");
            var decision = new CallEndDecision(9001L, 0L, LocalDateTime.of(2026, 10, 1, 9, 0),
                "NORMAL_CLEARING", "ALL_CHANNELS_ENDED", true);
            assertEquals(List.of(9001L), finalization.repair(List.of(decision)));
            assertEquals("9002", agents.presence("agent-a-work").getActiveCallId());
            assertEquals("BUSY", agents.presence("agent-b-work").getWorkStatus());
            assertEquals("NORMAL_END", jdbc.queryForObject("SELECT status FROM fcc_call_session WHERE id=9001", String.class));
            assertEquals("INTERRUPTED", jdbc.queryForObject("SELECT status FROM fcc_flow_step_execution WHERE id=9301", String.class));
            assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM fcc_flow_step_execution WHERE id=9302", String.class));
            assertEquals("ENDED", jdbc.queryForObject("SELECT status FROM fcc_flow_instance WHERE id=9001", String.class));
            assertEquals("UNKNOWN", jdbc.queryForObject("SELECT status FROM fcc_call_recording WHERE id=9501", String.class));
            assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM fcc_call_recording WHERE id=9502", String.class));
            assertNotNull(jdbc.queryForObject("SELECT ended_at FROM fcc_call_bridge WHERE id=9401", Object.class));
            assertNotNull(jdbc.queryForObject("SELECT left_at FROM fcc_call_bridge_member WHERE id=9402", Object.class));
            assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM fcc_dial_attempt WHERE id=9602", String.class));
            assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM fcc_dial_job WHERE id=9601", String.class));
            assertTrue(finalization.repair(List.of(decision)).isEmpty());
            finalization.cleanupEndedCall(9001L);
            assertEquals("9002", agents.presence("agent-a-work").getActiveCallId());
            assertEquals(1, mapper.candidates(0, LocalDateTime.of(2026, 10, 3, 0, 0), 100).size());

            // 仅完整快照缺失，不能将已有 answered_at 的未知通知任务推导成成功。
            jdbc.update("UPDATE fcc_dial_job SET status='RUNNING' WHERE id=9601");
            jdbc.update("UPDATE fcc_dial_attempt SET status='RUNNING' WHERE id=9602");
            jdbc.update("UPDATE fcc_call_session SET result='RECOVERY_UNKNOWN' WHERE id=9001");
            finalization.cleanupEndedCall(9001L);
            assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM fcc_dial_attempt WHERE id=9602", String.class));
            assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM fcc_dial_job WHERE id=9601", String.class));
        });
    }

    /**
     * 写入双坐席参与同 Call、客户仍在通话及关联残留的最小夹具。
     * @param jdbc 独立测试库访问器
     */
    private void seed(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO fcc_call_session(id,biz_id,ctrl_id,model_type,direction,status,started_at,answered_at,attributes) "
            + "VALUES(9001,'9602','ctrl-test','OUTBOUND_TWO_WAY_CALL','OUTBOUND','CONNECTED','2026-10-01 07:00:00','2026-10-01 07:01:00',"
            + "JSON_OBJECT('guestChannelUuid','guest','agentChannelUuid','agent-b')),"
            + "(9002,NULL,'ctrl-new','INBOUND_CUSTOMER_SERVICE','INBOUND','START','2026-10-01 10:00:00',NULL,JSON_OBJECT('guestChannelUuid','new-guest'))");
        jdbc.update("INSERT INTO fcc_agent(id,work_no,agent_name,status) VALUES(9101,'agent-a-work','测试 A','ENABLED'),(9102,'agent-b-work','测试 B','ENABLED')");
        jdbc.update("INSERT INTO fcc_agent_presence(agent_id,login_status,work_status,active_call_id,last_state_at) "
            + "VALUES(9101,'LOGIN','ANSWERED',9001,UTC_TIMESTAMP(3)),(9102,'LOGIN_BUSY','ANSWERED',9001,UTC_TIMESTAMP(3))");
        jdbc.update("INSERT INTO fcc_call_leg(id,call_id,channel_uuid,node_id,role_type,direction,state,created_time,metadata) "
            + "VALUES(9201,9001,'agent-a','test-node','AGENT','OUTBOUND','BRIDGE','2026-10-01 07:00:00',JSON_OBJECT('agentWorkNo','agent-a-work')),"
            + "(9202,9001,'agent-b','test-node','AGENT','OUTBOUND','BRIDGE','2026-10-01 07:00:00',JSON_OBJECT('agentWorkNo','agent-b-work')),"
            + "(9203,9001,'guest','test-node','CUSTOMER','INBOUND','BRIDGE','2026-10-01 07:00:00',NULL)");
        jdbc.update("INSERT INTO fcc_call_event(id,event_id,node_id,call_id,channel_uuid,event_type,event_time,normalized_payload) "
            + "VALUES(9251,'end-test','test-node',9001,'guest','Event.Channel','2026-10-01 09:00:00',JSON_OBJECT('state','DESTROY','cause','NORMAL_CLEARING'))");
        jdbc.update("INSERT INTO fcc_flow_instance(id,call_id,flow_definition_id,flow_version_id,status,started_at) VALUES(9001,9001,1,1,'RUNNING','2026-10-01 07:00:00')");
        jdbc.update("INSERT INTO fcc_flow_step_execution(id,flow_instance_id,call_id,step_key,action_type,status,started_at) "
            + "VALUES(9301,9001,9001,'wait','READ_DTMF','WAITING','2026-10-01 07:00:00'),(9302,9001,9001,'done','DIAL_CUSTOMER','SUCCEEDED','2026-10-01 07:00:00')");
        jdbc.update("INSERT INTO fcc_call_bridge(id,call_id,bridge_uuid,started_at) VALUES(9401,9001,'bridge-test','2026-10-01 07:00:00')");
        jdbc.update("INSERT INTO fcc_call_bridge_member(id,bridge_id,leg_id,joined_at) VALUES(9402,9401,9201,'2026-10-01 07:00:00')");
        jdbc.update("INSERT INTO fcc_call_recording(id,recording_id,call_id,status,object_key) "
            + "VALUES(9501,'test-record-1',9001,'RECORDING','/test/not-read.wav'),(9502,'test-record-2',9001,'COMPLETED','/test/not-read-2.wav')");
        jdbc.update("INSERT INTO fcc_dial_job(id,created_by,request_key,job_type,task_type,trigger_source,status) "
            + "VALUES(9601,'test','test-key','AUTO_FLOW','NOTIFY','FRONTEND','RUNNING')");
        jdbc.update("INSERT INTO fcc_dial_attempt(id,dial_job_id,call_id,attempt_no,destination_number,status,started_at) "
            + "VALUES(9602,9601,9001,1,'1001','RUNNING','2026-10-01 07:00:00')");
    }
}
