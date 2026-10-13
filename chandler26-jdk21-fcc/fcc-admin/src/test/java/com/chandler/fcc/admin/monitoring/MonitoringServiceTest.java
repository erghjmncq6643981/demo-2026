package com.chandler.fcc.admin.monitoring;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.dev33.satoken.stp.StpUtil;
import com.chandler.fcc.admin.monitoring.application.MonitoringService;
import com.chandler.fcc.admin.monitoring.controller.req.MonitoringReq;
import com.chandler.fcc.admin.monitoring.infrastructure.MonitoringMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 验证业务时区半开区间与完整聚合查询，不访问真实话单数据库。 */
class MonitoringServiceTest {
    /** 验证月、周、日边界统一转换 UTC，不受应用所在时区影响。 */
    @Test void usesBeijingPeriodBoundaries() {
        try(var permission=mockStatic(StpUtil.class)) {
            var mapper=mock(MonitoringMapper.class); var service=new MonitoringService(mapper);
            var request=new MonitoringReq(); request.setDate(LocalDate.of(2026,10,4));
            request.setPeriod("day"); service.aggregate(request);
            verify(mapper).aggregate(LocalDateTime.of(2026,10,3,16,0),LocalDateTime.of(2026,10,4,16,0),true);
            request.setPeriod("week"); service.aggregate(request);
            verify(mapper).aggregate(LocalDateTime.of(2026,9,27,16,0),LocalDateTime.of(2026,10,4,16,0),false);
            request.setPeriod("month"); service.aggregate(request);
            verify(mapper).aggregate(LocalDateTime.of(2026,9,30,16,0),LocalDateTime.of(2026,10,31,16,0),false);
            permission.verify(()->StpUtil.checkPermission("cdr:view"),times(3));
        }
    }

    /** 非支持周期不查询数据库，应用层不能依赖 Controller 单层校验。 */
    @Test void rejectsUnsupportedPeriod() {
        try(var permission=mockStatic(StpUtil.class)) {
            var mapper=mock(MonitoringMapper.class); var request=new MonitoringReq();
            request.setDate(LocalDate.of(2026,10,4)); request.setPeriod("year");
            assertThrows(ResponseStatusException.class,()->new MonitoringService(mapper).aggregate(request));
            verifyNoInteractions(mapper);
        }
    }

    /** 聚合所有周期事实，不采用 CDR 列表 LIMIT，也不将普通结束认定为接通。
     * @throws Exception XML 读取或解析失败
     */
    @Test void aggregatesFactsWithoutPageLimit() throws Exception {
        var configuration=new Configuration();
        try(var stream=Resources.getResourceAsStream("mapper/MonitoringMapper.xml")) {
            new XMLMapperBuilder(stream,configuration,"mapper/MonitoringMapper.xml",configuration.getSqlFragments()).parse();
        }
        String sql=configuration.getMappedStatement("com.chandler.fcc.admin.monitoring.infrastructure.MonitoringMapper.aggregate")
            .getBoundSql(Map.of("start",LocalDateTime.of(2026,10,1,0,0),"end",LocalDateTime.of(2026,11,1,0,0),"hourly",false)).getSql();
        assertFalse(sql.contains("LIMIT")); assertFalse(sql.contains("NORMAL_END"));
        assertTrue(sql.contains("COUNT(*)")); assertTrue(sql.contains("c.started_at>=? AND c.started_at<?"));
        assertTrue(sql.contains("EXISTS"));
    }
}
