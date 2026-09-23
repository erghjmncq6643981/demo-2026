import apiClient from "../../../api/apiClient";

const managementBaseUrl = "/api/admin/business";

export type DialJobAction = "PAUSE" | "RESUME" | "CANCEL";

export interface DialJobSummary {
  id: string;
  flowKey: string;
  createdBy: string;
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

export interface AutoDialVariables extends Record<string, unknown> {
  text: string;
  confirmDigit: string;
}

export interface CreateDialJobReq {
  number: string;
  flowKey: string;
  variables: AutoDialVariables;
  maxAttempts: number;
  requestKey: string;
}

export const dialJobManagementApi = {
  list(params: { page: number; flowKey?: string }): Promise<DialJobSummary[]> {
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
