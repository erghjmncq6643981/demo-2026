import apiClient from './apiClient';
import type { PageResult } from '../shared/api/page';

export interface WhitelistItem {
  realName: string;
  desc: string;
  defaultWorkNo: string;
  isSupervisor: boolean;
}

export interface AgentVO {
  id: string;
  workNo: string;
  realName?: string;
  agentName?: string;
  avatarUrl?: string;
  role?: string;
  roleCode?: string;
  isSupervisor?: boolean;
  phone?: string;
  phoneNumber?: string;
  email?: string;
  status: string;
  state?: string;
  boundExtension?: string;
  currentExtension?: string;
  boundEndpointType?: string;
  groupNames?: string[];
  createdAt?: string;
}

export interface AgentCreateReq {
  workNo: string;
  agentName: string;
  phoneNumber?: string;
  roleCode?: string;
  metadata?: string;
}

export interface AgentBindingReq {
  agentId: string;
  endpointType: string;
  endpointValue: string;
  priority?: number;
}

export interface AgentBindingVO {
  id: string;
  agentId: string;
  endpointType: string;
  extension?: string;
  status: string;
  createdAt?: string;
}

export interface AgentGroupVO {
  id: string;
  parentId?: string;
  groupCode: string;
  groupName: string;
  groupType?: string;
  routingStrategy?: string;
  status?: string;
  memberCount?: number;
  createdAt?: string;
}

export interface AgentGroupCreateReq {
  groupCode: string;
  groupName: string;
  parentId?: string;
  groupType?: string;
  routingStrategy?: string;
}

export interface AgentGroupUpdateReq {
  id: string;
  groupName?: string;
  groupCode?: string;
  groupType?: string;
  routingStrategy?: string;
  status?: string;
}

export interface AgentGroupMemberVO {
  id: string;
  groupId: string;
  agentId: string;
  workNo: string;
  agentName: string;
  phoneNumber?: string;
  memberRole: string; // LEADER, MEMBER
  priority: number;
  roleCode?: string;
  status?: string;
  createdAt?: string;
}

export interface AgentGroupMemberReq {
  groupId: string;
  agentId: string;
  memberRole?: string;
  priority?: number;
}

export interface AgentCreateAndBindGroupReq {
  workNo: string;
  agentName: string;
  phoneNumber?: string;
  roleCode?: string;
  memberRole?: string;
  priority?: number;
}

export const agentApi = {
  getWhitelist(): Promise<WhitelistItem[]> {
    return apiClient.get('/agents/whitelist');
  },
  list(params?: { pageNum?: number; pageSize?: number; workNo?: string; realName?: string; status?: string; role?: string }): Promise<PageResult<AgentVO>> {
    return apiClient.get('/agents', { params });
  },
  get(id: string): Promise<AgentVO> {
    return apiClient.get(`/agents/${id}`);
  },
  create(data: AgentCreateReq): Promise<string> {
    return apiClient.post('/agents', data);
  },
  delete(id: string): Promise<void> {
    return apiClient.delete(`/agents/${id}`);
  },
  bindEndpoint(data: AgentBindingReq): Promise<string> {
    return apiClient.post('/agents/bindings', data);
  },
  listBindings(agentId: string): Promise<AgentBindingVO[]> {
    return apiClient.get(`/agents/${agentId}/bindings`);
  },
  listGroups(): Promise<AgentGroupVO[]> {
    return apiClient.get('/agents/groups');
  },
  createGroup(data: AgentGroupCreateReq): Promise<string> {
    return apiClient.post('/agents/groups', data);
  },
  updateGroup(data: AgentGroupUpdateReq): Promise<void> {
    return apiClient.put('/agents/groups', data);
  },
  deleteGroup(id: string): Promise<void> {
    return apiClient.delete(`/agents/groups/${id}`);
  },
  listGroupMembers(groupId: string): Promise<AgentGroupMemberVO[]> {
    return apiClient.get(`/agents/groups/${groupId}/members`);
  },
  addMemberToGroup(data: AgentGroupMemberReq): Promise<void> {
    return apiClient.post('/agents/groups/members', data);
  },
  createAndBindAgent(groupId: string, data: AgentCreateAndBindGroupReq): Promise<string> {
    return apiClient.post(`/agents/groups/${groupId}/create-and-bind`, data);
  },
  updateGroupMember(groupId: string, agentId: string, data: { memberRole?: string; priority?: number }): Promise<void> {
    return apiClient.put(`/agents/groups/${groupId}/members/${agentId}`, data);
  },
  removeMemberFromGroup(groupId: string, agentId: string): Promise<void> {
    return apiClient.delete(`/agents/groups/${groupId}/members/${agentId}`);
  },
  resetPassword(agentId: string, password?: string): Promise<{ loginPassword?: string; sipPassword?: string }> {
    return apiClient.post(`/agents/${agentId}/reset-password`, { password: password || undefined });
  },
};

