package com.chandler.fcc.admin.starter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
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
 * 话单查询必须使用登录会话的租户范围，不能回退为全库查询。
 */
class CallCdrTenantScopeTest {

  /** 列表条件和统计 SQL 都接收当前租户 ID。 */
  @Test
  void scopesListAndStatsToAuthenticatedTenant() {
    var assistant = new MapperBuilderAssistant(
      new MybatisConfiguration(),
      "tenant-scope-test"
    );
    assistant.setCurrentNamespace("tenant-scope-test");
    TableInfoHelper.initTableInfo(assistant, CallSessionEntity.class);
    var sessions = mock(AdminCallSessionMapper.class);
    var service = new CallCdrService(
      sessions,
      mock(AdminCallLegMapper.class),
      mock(AdminCallRecordingMapper.class),
      mock(AgentMapper.class)
    );
    var loginSession = mock(SaSession.class);
    when(loginSession.get("tenantId")).thenReturn(42L);
    when(
      sessions.selectPage(any(Page.class), any(LambdaQueryWrapper.class))
    ).thenReturn(new Page<CallSessionEntity>(1, 10));
    when(
      sessions.selectStatsSince(eq(42L), any(LocalDateTime.class))
    ).thenReturn(Map.of());

    try (var authentication = mockStatic(StpUtil.class)) {
      authentication.when(StpUtil::getSession).thenReturn(loginSession);
      service.queryCdrs(
        CallCdrQueryReq.builder().pageNum(1).pageSize(10).build()
      );
      service.queryTodayStats();
    }

    var wrapper = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    verify(sessions).selectPage(any(Page.class), wrapper.capture());
    assertTrue(wrapper.getValue().getSqlSegment().contains("tenant_id"));
    verify(sessions).selectStatsSince(eq(42L), any(LocalDateTime.class));
  }
}
