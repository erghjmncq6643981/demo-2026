import apiClient from "../../../api/apiClient";

const managementBaseUrl = "/api/telephony/management";

export interface CustomerSummary {
  id: string;
  name: string;
  phoneNumber: string;
  companyName?: string;
  owner: string;
  version: string;
}

export interface CustomerDetail extends CustomerSummary {
  notes?: string;
}

export interface SaveCustomerReq {
  name: string;
  phoneNumber: string;
  companyName?: string;
  notes?: string;
  version?: string;
}

export const customerManagementApi = {
  list(params: {
    page: number;
    owner?: string;
    phone?: string;
  }): Promise<CustomerSummary[]> {
    return apiClient.get("/customers", { baseURL: managementBaseUrl, params });
  },
  detail(id: string): Promise<CustomerDetail> {
    return apiClient.get(`/customers/${encodeURIComponent(id)}`, {
      baseURL: managementBaseUrl,
    });
  },
  create(owner: string, data: SaveCustomerReq): Promise<string> {
    return apiClient.post("/customers", data, {
      baseURL: managementBaseUrl,
      params: { owner },
    });
  },
  update(id: string, data: SaveCustomerReq): Promise<string> {
    return apiClient.put(`/customers/${encodeURIComponent(id)}`, data, {
      baseURL: managementBaseUrl,
    });
  },
};
