import apiClient from "./apiClient";
import type { PageResult } from "../shared/api/page";

export type FlowTemplateType = "INBOUND";

export interface FlowTypeVO {
  code: FlowTemplateType;
  label: string;
  description: string;
}

export interface FlowVersionVO {
  id: string;
  version: string;
  versionNo: number;
  publishStatus: string;
  definitionJson?: string;
  publishedAt?: string;
  createdBy?: string;
  createdAt?: string;
}

export interface FlowDefinitionVO {
  id: string;
  flowKey: string;
  flowName: string;
  modelType: FlowTemplateType;
  status: string;
  currentVersion?: string;
  system: boolean;
}

export interface FlowSaveDraftReq {
  definitionJson: string;
}

export interface FlowPublishReq {
  version: string;
  remark?: string;
}

export interface FlowActionVO {
  code: string;
  label: string;
  executorType: string;
  executorTypeLabel: string;
  operation: string;
  fNodeMethod?: string;
}

export interface SystemFlowModelVO {
  template: string;
  definition: {
    nodes?: Array<{
      key: string;
      label: string;
      action: string;
      actionLabel?: string;
      executorType?: string;
      executorTypeLabel?: string;
      operation?: string;
      fNodeMethod?: string;
    }>;
  };
}

export interface FlowPublishResp {
  version: string;
  publishStatus: string;
  runtimeActivationStatus: string;
}

export interface FlowValidationResp {
  valid: boolean;
  normalizedDefinitionJson: string;
}

export interface FlowExecutionResp {
  instance: {
    id?: string;
    callId?: string;
    versionId?: string;
    status?: string;
    currentStep?: string;
    snapshot?: string;
    startedAt?: string;
    endedAt?: string;
  };
  steps: Array<{
    id: string;
    stepKey: string;
    actionType: string;
    attemptNo: number;
    status: string;
    commandId?: string;
    eventId?: string;
    input?: string;
    output?: string;
    errorCode?: string;
    startedAt: string;
    endedAt?: string;
    durationMs?: number;
  }>;
  nextCursor: string;
}

export const flowApi = {
  create(data: {
    flowKey: string;
    flowName: string;
    modelType: FlowTemplateType;
  }): Promise<FlowDefinitionVO> {
    return apiClient.post("/flow-studio/flows", data);
  },
  types(): Promise<FlowTypeVO[]> {
    return apiClient.get("/flow-studio/types");
  },
  list(params: {
    pageNum: number;
    pageSize: number;
  }): Promise<PageResult<FlowDefinitionVO>> {
    return apiClient.get("/flow-studio/flows", { params });
  },
  getVersions(
    flowKey: string,
    params: { pageNum: number; pageSize: number },
  ): Promise<PageResult<FlowVersionVO>> {
    return apiClient.get(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/versions`,
      { params },
    );
  },
  getVersion(flowKey: string, versionNo: number): Promise<FlowVersionVO> {
    return apiClient.get(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/versions/${versionNo}`,
    );
  },
  createDraftFromVersion(
    flowKey: string,
    versionNo: number,
  ): Promise<FlowVersionVO> {
    return apiClient.post(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/drafts/from-version/${versionNo}`,
    );
  },
  saveDraft(flowKey: string, data: FlowSaveDraftReq): Promise<FlowVersionVO> {
    return apiClient.put(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/draft`,
      data,
    );
  },
  validate(flowKey: string, data: FlowSaveDraftReq): Promise<FlowValidationResp> {
    return apiClient.post(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/validate`,
      data,
    );
  },
  publish(flowKey: string, data: FlowPublishReq): Promise<FlowPublishResp> {
    return apiClient.post(
      `/flow-studio/flows/${encodeURIComponent(flowKey)}/publish`,
      data,
    );
  },
  actions(): Promise<FlowActionVO[]> {
    return apiClient.get("/flow-studio/actions");
  },
  model(template: string): Promise<SystemFlowModelVO> {
    return apiClient.get(`/flow-studio/models/${encodeURIComponent(template)}`);
  },
  models(): Promise<SystemFlowModelVO[]> {
    return apiClient.get("/flow-studio/models");
  },
  execution(callId: string, after: string): Promise<FlowExecutionResp> {
    return apiClient.get(`/flow-studio/calls/${encodeURIComponent(callId)}`, {
      params: { after },
    });
  },
};
