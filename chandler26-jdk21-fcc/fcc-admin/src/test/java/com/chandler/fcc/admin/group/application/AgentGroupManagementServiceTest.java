package com.chandler.fcc.admin.group.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentGroupEntity;
import com.chandler.fcc.admin.infrastructure.persistence.entity.AgentGroupMemberEntity;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentGroupMapper;
import com.chandler.fcc.admin.infrastructure.persistence.mapper.AgentGroupMemberMapper;
import com.chandler.fcc.admin.model.dto.AgentGroupMemberReq;
import org.junit.jupiter.api.Test;

/** 组织管理职责提取后保持成员分页和重复绑定的既有契约。 */
class AgentGroupManagementServiceTest {
    /** 重复成员绑定不产生第二条成员事实。 */
    @Test
    void duplicateMemberRemainsIdempotent() {
        AgentGroupMapper groups = mock(AgentGroupMapper.class);
        AgentGroupMemberMapper members = mock(AgentGroupMemberMapper.class);
        when(members.selectCount(any())).thenReturn(1L);
        new AgentGroupManagementService(groups, members).addMemberToGroup(
            AgentGroupMemberReq.builder().groupId(1L).agentId(2L).build());
        verify(members, never()).insert(any(AgentGroupMemberEntity.class));
    }

    /** 分页限制与空结果保持在应用服务，页面不能替代该校验。 */
    @Test
    void validatesPaginationAndReturnsEmptySummary() {
        AgentGroupMapper groups = mock(AgentGroupMapper.class);
        AgentGroupMemberMapper members = mock(AgentGroupMemberMapper.class);
        when(groups.selectById(1L)).thenReturn(AgentGroupEntity.builder().id(1L).status("ENABLED").build());
        AgentGroupManagementService service = new AgentGroupManagementService(groups, members);
        assertThrows(IllegalArgumentException.class, () -> service.listGroupMembers(1L, 1, 101, null));
        when(members.countSubtreeMembers(1L, null)).thenReturn(0L);
        assertEquals(0L, service.listGroupMembers(1L, 1, 10, null).getTotal());
        verify(members, never()).selectSubtreeMembers(any(), any(), anyLong(), anyInt());
    }
}
