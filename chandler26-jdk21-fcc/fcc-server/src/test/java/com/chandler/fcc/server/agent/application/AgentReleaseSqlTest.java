package com.chandler.fcc.server.agent.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/** 静态验证统一释放 SQL 的 MyBatis 解析和身份保护，不冒充真实数据库执行。 */
class AgentReleaseSqlTest {
    /**
     * 三个释放入口共享有效参与保护，并只按原通话、原登录状态更新。
     * @throws Exception Mapper 资源或解析失败
     */
    @Test
    void everyReleasePathHasTheSameParticipationGuard() throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : List.of("mapper/AgentRuntimeMapper.xml", "mapper/CallEndMapper.xml")) {
            try (var input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        for (String statement : List.of(
            "com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper.release",
            "com.chandler.fcc.server.agent.infrastructure.AgentRuntimeMapper.releaseEndedLeg",
            "com.chandler.fcc.server.call.infrastructure.persistence.mapper.CallEndMapper.releaseAgents")) {
            String sql = configuration.getMappedStatement(statement).getBoundSql(
                Map.of("owner", "agent-test", "callId", 9001L, "channelUuid", "agent-leg", "ids", List.of(9001L))
            ).getSql();
            assertTrue(sql.contains("live.call_id = p.active_call_id"), statement);
            assertTrue(sql.contains("live.state != 'DESTROY' OR live.ended_at IS NULL"), statement);
            assertTrue(sql.contains("known_owner.leg_id = live.id"), statement);
            assertTrue(sql.contains("WHEN 'LOGIN_BUSY' THEN 'BUSY'"), statement);
            assertTrue(sql.contains("WHEN 'LOGIN' THEN 'READY'"), statement);
            assertFalse(sql.contains("THEN 'ACW'"), statement);
        }
    }
}
