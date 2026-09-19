import apiClient from '../../../api/apiClient';

export interface ClientVersionVO {
  id: string;
  version: string;
  platform: string;
  downloadUrl: string;
  fileMd5?: string;
  forceUpdate: boolean;
  status: string;
  releaseNotes?: string;
  releasedAt?: string;
  createdBy?: string;
}

export interface ClientHardwareRecordVO {
  id: string;
  agentId: string;
  workNum: string;
  clientVersion?: string;
  macAddr?: string;
  os?: string;
  ipAddr?: string;
  loginTime?: string;
}

export interface ClientVersionReleaseReq {
  version: string;
  platform: string;
  downloadUrl: string;
  fileMd5?: string;
  forceUpdate: boolean;
  releaseNotes?: string;
}

export const clientFleetApi = {
  listVersions(platform?: string): Promise<ClientVersionVO[]> {
    return apiClient.get('/fleet/versions', { params: platform ? { platform } : undefined });
  },
  publishVersion(data: ClientVersionReleaseReq): Promise<string> {
    return apiClient.post('/fleet/versions', data);
  },
  listHardware(agentId?: string): Promise<ClientHardwareRecordVO[]> {
    return apiClient.get('/fleet/hardware', { params: agentId ? { agentId } : undefined });
  },
};
