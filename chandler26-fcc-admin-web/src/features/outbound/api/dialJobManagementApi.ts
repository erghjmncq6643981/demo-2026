import apiClient from "../../../api/apiClient";

const managementBaseUrl = "/api/admin/business";

export type DialJobAction = "PAUSE" | "RESUME" | "CANCEL";

export interface DialJobSummary {
  id: string;
  flowKey: string;
  taskType: "NOTIFY";
  triggerSource: "FRONTEND" | "API" | "MQ";
  bizId?: string;
  createdBy: string;
  status: string;
  maxAttempts: number;
  number: string;
  text: string;
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
  number: string;
  text: string;
  confirmDigit: string;
  timeoutSeconds: number;
  bizId?: string;
  maxAttempts: number;
  requestKey: string;
}

export const dialJobManagementApi = {
  list(params: { page: number; triggerSource?: string }): Promise<DialJobSummary[]> {
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
