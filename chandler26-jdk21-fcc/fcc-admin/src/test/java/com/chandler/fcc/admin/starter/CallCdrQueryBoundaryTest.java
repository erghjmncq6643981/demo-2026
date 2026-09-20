package com.chandler.fcc.admin.starter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chandler.fcc.admin.infrastructure.persistence.entity.CallSessionEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallLegMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallRecordingMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AdminCallSessionMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentMapper;
import com.chandler.fcc.admin.model.dto.CallCdrQueryReq;
import com.chandler.fcc.admin.service.CallCdrService;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 验证话单列表和统计均直接查询当前呼叫中心事实。
 */
class CallCdrQueryBoundaryTest {

    /**
     * 坐席筛选下推到数据库，统计接口接收同一时间边界。
     */
    @Test
    void appliesAgentFilterAndStatsBoundary() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
            new MybatisConfiguration(),
            "call-cdr-query-test"
        );
        assistant.setCurrentNamespace("call-cdr-query-test");
        TableInfoHelper.initTableInfo(assistant, CallSessionEntity.class);

        AdminCallSessionMapper sessions = mock(AdminCallSessionMapper.class);
        CallCdrService service = new CallCdrService(
            sessions,
            mock(AdminCallLegMapper.class),
            mock(AdminCallRecordingMapper.class),
            mock(AgentMapper.class)
        );
        when(sessions.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(
            new Page<CallSessionEntity>(1, 10)
        );
        when(sessions.selectStatsSince(any(LocalDateTime.class))).thenReturn(Map.of());

        try (var authentication = mockStatic(StpUtil.class)) {
            service.queryCdrs(
                CallCdrQueryReq.builder()
                    .agentWorkNo("901001")
                    .pageNum(1)
                    .pageSize(10)
                    .build()
            );
            service.queryTodayStats();
        }

        ArgumentCaptor<LambdaQueryWrapper<CallSessionEntity>> wrapper = ArgumentCaptor.forClass(
            LambdaQueryWrapper.class
        );
        verify(sessions).selectPage(any(Page.class), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("agent_work_no"));
        verify(sessions).selectStatsSince(any(LocalDateTime.class));
    }
}
