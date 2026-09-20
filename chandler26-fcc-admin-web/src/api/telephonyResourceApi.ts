import apiClient from "./apiClient";

export interface DidNumberVO {
  id: string;
  phoneNumber: string;
  trunkId?: string;
  routeKey?: string;
  status: string;
  createdAt?: string;
}

export const telephonyResourceApi = {
  listDids(): Promise<DidNumberVO[]> {
    return apiClient.get("/resources/dids");
  },
  bindDidFlow(id: string, flowKey: string): Promise<void> {
    return apiClient.put(
      `/resources/dids/${encodeURIComponent(id)}/flow-binding`,
      { flowKey },
    );
  },
  unbindDidFlow(id: string): Promise<void> {
    return apiClient.delete(
      `/resources/dids/${encodeURIComponent(id)}/flow-binding`,
    );
  },
};
