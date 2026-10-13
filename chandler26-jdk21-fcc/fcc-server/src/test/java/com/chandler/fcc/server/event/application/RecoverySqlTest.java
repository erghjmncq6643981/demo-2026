package com.chandler.fcc.server.event.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/** 验证恢复领取与参与事实 SQL 的解析和幂等条件，不代替 MySQL 执行验证。 */
class RecoverySqlTest {
    /** @throws Exception Mapper 无法读取或解析 */
    @Test void validatesClaimAndParticipationSql() throws Exception {
        var configuration=new Configuration();
        for(String resource:List.of("mapper/EventInboxMapper.xml","mapper/AgentParticipationMapper.xml")) {
            try(var stream=Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(stream,configuration,resource,configuration.getSqlFragments()).parse();
            }
        }
        String prefix="com.chandler.fcc.server.event.infrastructure.EventInboxMapper.";
        String claim=configuration.getMappedStatement(prefix+"claimRecovery").getBoundSql(Map.of("id","event","token","lease","actor","operator")).getSql();
        assertTrue(claim.contains("recovery_attempt_count<10"));
        assertTrue(claim.contains("INTERVAL 5 MINUTE"));
        String finish=configuration.getMappedStatement(prefix+"finishRecovery").getBoundSql(Map.of("id","event","token","lease","status","UNKNOWN","error","type")).getSql();
        assertTrue(finish.contains("recovery_token=?"));
        String participation=configuration.getMappedStatement("com.chandler.fcc.server.agent.infrastructure.AgentParticipationMapper.synchronizeLeg")
            .getBoundSql(Map.of("legId",1L,"participationId",2L)).getSql();
        assertTrue(participation.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(participation.contains("$.agentWorkNo"));
    }
}
