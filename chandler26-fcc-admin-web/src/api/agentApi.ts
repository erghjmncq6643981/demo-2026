import apiClient from './apiClient';
import type { PageResult } from '../shared/api/page';

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

export interface AccountCredentialVO {
  subjectType?: string;
  id?: string;
  account: string;
  displayName: string;
  initialPassword?: string;
  extensionSecret?: string;
  hint?: string;
}

export interface AgentCreateReq {
  workNo: string;
  agentName: string;
  phoneNumber?: string;
  roleCode?: string;
  password?: string;
  metadata?: string;
}

export interface AgentUpdateReq {
  id: string | number;
  agentName?: string;
  phoneNumber?: string;
  roleCode?: string;
  status?: string;
  metadata?: string;
}

export interface AgentEndpointsResp {
  workNo: string;
  agentName: string;
  activeEndpointType: 'WEBRTC' | 'SIP' | 'MOBILE';
  activeEndpointValue: string;
  webrtcWorkNo?: string;
  sipExtension?: string;
  mobilePhone?: string;
  availableSipExtensions: string[];
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
  groupName: string;
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
  password?: string;
  memberRole?: string;
  priority?: number;
}

export const agentApi = {
  list(params?: { pageNum?: number; pageSize?: number; workNo?: string; realName?: string; status?: string; role?: string }): Promise<PageResult<AgentVO>> {
    return apiClient.get('/agents', { params });
  },
  get(id: string): Promise<AgentVO> {
    return apiClient.get(`/agents/${id}`);
  },
  create(data: AgentCreateReq): Promise<AccountCredentialVO> {
    return apiClient.post('/agents', data);
  },
  update(data: AgentUpdateReq): Promise<void> {
    return apiClient.put('/agents', data);
  },
  delete(id: string): Promise<void> {
    return apiClient.delete(`/agents/${id}`);
  },
  endpoints(workNo: string): Promise<AgentEndpointsResp> {
    return apiClient.get(`/agents/${encodeURIComponent(workNo)}/endpoints`);
  },
  switchEndpoint(data: { workNo: string; endpointType: 'WEBRTC' | 'SIP'; endpointValue?: string }): Promise<AgentEndpointsResp> {
    return apiClient.post('/agents/switch-endpoint', data);
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
  listGroupMembers(
    groupId: string,
    params: { pageNum: number; pageSize: number; keyword?: string },
  ): Promise<PageResult<AgentGroupMemberVO>> {
    return apiClient.get(`/agents/groups/${groupId}/members`, { params });
  },
  addMemberToGroup(data: AgentGroupMemberReq): Promise<void> {
    return apiClient.post('/agents/groups/members', data);
  },
  createAndBindAgent(groupId: string, data: AgentCreateAndBindGroupReq): Promise<AccountCredentialVO> {
    return apiClient.post(`/agents/groups/${groupId}/create-and-bind`, data);
  },
  updateGroupMember(groupId: string, agentId: string, data: { memberRole?: string; priority?: number }): Promise<void> {
    return apiClient.put(`/agents/groups/${groupId}/members/${agentId}`, data);
  },
  removeMemberFromGroup(groupId: string, agentId: string): Promise<void> {
    return apiClient.delete(`/agents/groups/${groupId}/members/${agentId}`);
  },
  resetPassword(agentId: string | number, password?: string): Promise<AccountCredentialVO> {
    return apiClient.post(`/agents/${agentId}/reset-password`, { password: password || undefined });
  },
};

