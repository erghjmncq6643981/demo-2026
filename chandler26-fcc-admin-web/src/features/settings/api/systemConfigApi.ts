import apiClient from '../../../api/apiClient';

export type ConfigScope = 'WEB' | 'BACKEND' | 'CLIENT' | 'SYSTEM';
export type ConfigValueType = 'STRING' | 'JSON' | 'INT' | 'BOOLEAN';

export interface SystemConfigVO {
  id: string;
  propName: string;
  propValue: string;
  propType: ConfigValueType;
  scope: ConfigScope;
  description?: string;
  updatedBy?: string;
  updatedAt?: string;
}

export interface SystemConfigSaveReq {
  propName: string;
  propValue: string;
  propType: ConfigValueType;
  scope: ConfigScope;
  description?: string;
}

export const systemConfigApi = {
  list(scope: ConfigScope): Promise<SystemConfigVO[]> {
    return apiClient.get('/configs', { params: { scope } });
  },
  save(data: SystemConfigSaveReq): Promise<string> {
    return apiClient.post('/configs', data);
  },
  delete(id: string): Promise<void> {
    return apiClient.delete(`/configs/${id}`);
  },
};
