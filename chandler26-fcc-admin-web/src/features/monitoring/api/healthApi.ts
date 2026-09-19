import apiClient from '../../../api/apiClient';

export interface SidecarHealthVO {
  status: string;
  nodeId?: string;
  nodeState?: string;
  freeSwitchAlive: boolean;
  databaseConnected: boolean;
  activeChannels?: number;
  maxChannels?: number;
  checkedAt: string;
  message?: string;
}

export const healthApi = {
  getSidecarHealth(): Promise<SidecarHealthVO> {
    return apiClient.get('/operations/sidecar/health');
  },
};
