import apiClient from "../../../api/apiClient";

const managementBaseUrl = "/api/telephony/management";

export type DialMode = "PROGRESSIVE" | "NOTIFICATION";
export type DialJobAction = "PAUSE" | "RESUME" | "CANCEL";

export interface DialJobSummary {
  id: string;
  owner: string;
  mode: DialMode;
  status: string;
  maxAttempts: number;
  number: string;
  scheduledAt?: string;
}

export interface DialAttempt {
  id: string;
  callId?: string;
  attemptNo: number;
  status: string;
  result?: string;
  failureReason?: string;
  startedAt?: string;
  endedAt?: string;
}

export interface CreateDialJobReq {
  owner: string;
  number: string;
  mode: DialMode;
  maxAttempts: number;
  requestKey: string;
}

export const dialJobManagementApi = {
  list(params: { page: number; owner?: string }): Promise<DialJobSummary[]> {
    return apiClient.get("/dial-jobs", { baseURL: managementBaseUrl, params });
  },
  create(data: CreateDialJobReq): Promise<string> {
    return apiClient.post("/dial-jobs", data, { baseURL: managementBaseUrl });
  },
  attempts(id: string): Promise<DialAttempt[]> {
    return apiClient.get(`/dial-jobs/${encodeURIComponent(id)}/attempts`, {
      baseURL: managementBaseUrl,
    });
  },
  control(id: string, action: DialJobAction): Promise<{ status: string }> {
    return apiClient.post(
      `/dial-jobs/${encodeURIComponent(id)}/${action}`,
      undefined,
      {
        baseURL: managementBaseUrl,
      },
    );
  },
};
