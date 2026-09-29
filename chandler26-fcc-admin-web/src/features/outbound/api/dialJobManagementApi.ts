import apiClient from "../../../api/apiClient";

const managementBaseUrl = "/api/admin/business";

export type DialJobAction = "PAUSE" | "RESUME" | "CANCEL";

export interface DialJobSummary {
  id: string;
  flowKey: string;
  taskType: "NOTIFY" | "SURVEY";
  triggerSource: "FRONTEND" | "API" | "MQ";
  bizId?: string;
  createdBy: string;
  status: string;
  maxAttempts: number;
  number: string;
  text: string;
  scheduledAt?: string;
  talkDurationMs?: number | null;
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
  taskType: "NOTIFY" | "SURVEY";
  number: string;
  text: string;
  confirmDigit?: string;
  timeoutSeconds?: number;
  bizId?: string;
  maxAttempts: number;
  requestKey: string;
}

export interface DialJobQueryReq {
  page: number;
  pageSize?: number;
  number?: string;
  triggerSource?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
}

export interface DialJobPageResult {
  list: DialJobSummary[];
  total: number;
  page?: number;
  pageSize?: number;
}

export const dialJobManagementApi = {
  list(params: DialJobQueryReq): Promise<DialJobPageResult | DialJobSummary[]> {
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
