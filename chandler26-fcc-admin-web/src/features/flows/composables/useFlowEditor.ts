import { computed, onMounted, ref } from "vue";
import {
  flowApi,
  type FlowActionVO,
  type FlowDefinitionVO,
  type FlowTemplateType,
  type FlowTypeVO,
  type FlowVersionVO,
  type SystemFlowModelVO,
} from "../../../api/flowApi";
import {
  readStagedFlow,
  validateStagedFlow,
  type FlowModelNode,
  type FlowValidationIssue,
} from "../model/stagedFlow";
import { confirmAction, errorText } from "../../../utils/feedback";
import { parseFlowDefinition } from "../model/flowDefinition";

/** Owns backend flow/version selection and explicit draft/publication outcomes. */
export function useFlowEditor() {
  const flows = ref<FlowDefinitionVO[]>([]);
  const flowPage = ref(1);
  const flowTotal = ref(0);
  const flowTypes = ref<FlowTypeVO[]>([]);
  const systemModels = ref<SystemFlowModelVO[]>([]);
  const selectedFlow = ref<FlowDefinitionVO | null>(null);
  const versions = ref<FlowVersionVO[]>([]);
  const versionPage = ref(1);
  const versionTotal = ref(0);
  const selectedVersion = ref("");
  const definition = ref("");
  const savedDefinition = ref("");
  const loading = ref(false);
  const pending = ref(false);
  const validating = ref(false);
  const createPending = ref(false);
  const error = ref("");
  const outcome = ref("");
  const modelNodes = ref<FlowModelNode[]>([]);
  const actionCatalog = ref<FlowActionVO[]>([]);
  const validationIssues = ref<FlowValidationIssue[]>([]);
  const validatedDefinition = ref("");
  const dirty = computed(() => definition.value !== savedDefinition.value);
  const validated = computed(
    () => !!definition.value && definition.value === validatedDefinition.value,
  );
  const version = computed(() =>
    versions.value.find((item) => item.version === selectedVersion.value),
  );

  function applyDefinition(record?: FlowVersionVO) {
    selectedVersion.value = record?.version || "";
    definition.value = record?.definitionJson || "";
    savedDefinition.value = definition.value;
    validationIssues.value = localValidationIssues(definition.value);
    validatedDefinition.value = "";
  }

  function localValidationIssues(source: string): FlowValidationIssue[] {
    if (!source) return [];
    try {
      const parsed = readStagedFlow(source);
      return parsed
        ? validateStagedFlow(parsed)
        : [{ stage: "ENTRY", field: "definition", message: "流程定义不能为空" }];
    } catch (cause) {
      return [{ stage: "ENTRY", field: "definition", message: errorText(cause) }];
    }
  }

  async function editVersion() {
    if (
      !selectedFlow.value ||
      selectedFlow.value.system ||
      !version.value ||
      pending.value ||
      loading.value
    )
      return;
    const hadDraft = versions.value.some((item) => item.publishStatus === "DRAFT");
    pending.value = true;
    error.value = "";
    try {
      const draft = await flowApi.createDraftFromVersion(
        selectedFlow.value.flowKey,
        version.value.versionNo,
      );
      versionPage.value = 1;
      await refreshVersions(draft.version);
      outcome.value = hadDraft
        ? "已打开现有草稿；修改只影响草稿，当前发布版本继续生效。"
        : "已从所选版本派生草稿；修改只影响草稿，当前发布版本继续生效。";
    } catch (cause) {
      error.value = errorText(cause);
    } finally {
      pending.value = false;
    }
  }

  async function loadVersion(value: string) {
    const summary = versions.value.find((item) => item.version === value);
    if (!summary || !selectedFlow.value) {
      applyDefinition();
      return;
    }
    const detail = await flowApi.getVersion(
      selectedFlow.value.flowKey,
      summary.versionNo,
    );
    applyDefinition(detail);
  }

  async function canDiscard() {
    return (
      !dirty.value ||
      (await confirmAction("当前定义尚未保存，确认放弃这些修改？"))
    );
  }

  async function refreshVersions(preferred?: string) {
    if (!selectedFlow.value) return;
    const result = await flowApi.getVersions(selectedFlow.value.flowKey, {
      pageNum: versionPage.value,
      pageSize: 20,
    });
    versions.value = result.list;
    versionTotal.value = result.total;
    await loadVersion(
      versions.value.find((item) => item.version === preferred)?.version ||
        versions.value.find((item) => item.publishStatus === "DRAFT")
          ?.version ||
        versions.value[0]?.version ||
        "",
    );
  }

  async function selectFlow(flow: FlowDefinitionVO) {
    if (loading.value || pending.value || !(await canDiscard())) return;
    loading.value = true;
    error.value = "";
    outcome.value = "";
    selectedFlow.value = flow;
    versionPage.value = 1;
    versions.value = [];
    applyDefinition();
    try {
      await Promise.all([refreshVersions(), loadModel(flow.modelType)]);
    } catch (cause) {
      error.value = errorText(cause);
    } finally {
      loading.value = false;
    }
  }

  async function selectVersion(value: string) {
    if (loading.value || pending.value || !(await canDiscard())) return;
    await loadVersion(value);
    outcome.value = "";
  }

  async function reload(page = flowPage.value) {
    if (loading.value || pending.value || !(await canDiscard())) return;
    loading.value = true;
    error.value = "";
    try {
      flowPage.value = page;
      const [flowResult, actions, types, models] = await Promise.all([
        flowApi.list({ pageNum: flowPage.value, pageSize: 20 }),
        flowApi.actions(),
        flowApi.types(),
        flowApi.models(),
      ]);
      flows.value = flowResult.list;
      flowTotal.value = flowResult.total;
      flowTypes.value = types;
      systemModels.value = models;
      actionCatalog.value = actions;
      selectedFlow.value =
        flows.value.find(
          (item) => item.flowKey === selectedFlow.value?.flowKey,
        ) ||
        flows.value[0] ||
        null;
      versions.value = [];
      applyDefinition();
      if (selectedFlow.value) {
        await Promise.all([
          refreshVersions(),
          loadModel(selectedFlow.value.modelType),
        ]);
      } else {
        modelNodes.value = [];
      }
    } catch (cause) {
      error.value = errorText(cause);
    } finally {
      loading.value = false;
    }
  }

  async function selectFlowPage(page: number) {
    if (page === flowPage.value || !(await canDiscard())) return;
    selectedFlow.value = null;
    versionPage.value = 1;
    applyDefinition();
    await reload(page);
  }

  async function selectVersionPage(page: number) {
    if (page === versionPage.value || !(await canDiscard())) return;
    versionPage.value = page;
    loading.value = true;
    try {
      await refreshVersions();
    } catch (cause) {
      error.value = errorText(cause);
    } finally {
      loading.value = false;
    }
  }

  async function createFlow(
    flowKey: string,
    flowName: string,
    modelType: FlowTemplateType,
  ) {
    if (createPending.value || loading.value || pending.value) return null;
    if (!(await canDiscard())) return null;
    createPending.value = true;
    error.value = "";
    try {
      const created = await flowApi.create({
        flowKey: flowKey.trim(),
        flowName: flowName.trim(),
        modelType,
      });
      selectedFlow.value = created;
      versionPage.value = 1;
      applyDefinition();
      await reload(1);
      outcome.value = "流程与首个草稿已创建，请补齐业务参数并保存。";
      return created;
    } catch (cause) {
      error.value = errorText(cause);
      return null;
    } finally {
      createPending.value = false;
    }
  }

  async function saveDraft() {
    if (!selectedFlow.value || pending.value || loading.value) return;
    error.value = "";
    try {
      assertDraftStructure();
    } catch (cause) {
      error.value = errorText(cause);
      return;
    }
    pending.value = true;
    try {
      const serverValidated = validatedDefinition.value === definition.value;
      const validatedSource = definition.value;
      const saved = await flowApi.saveDraft(selectedFlow.value.flowKey, {
        definitionJson: definition.value,
      });
      versionPage.value = 1;
      await refreshVersions(saved.version);
      if (serverValidated && definition.value === validatedSource) {
        validatedDefinition.value = definition.value;
      }
      outcome.value = validated.value
        ? "草稿已保存并保留服务端校验结果，尚未发布。"
        : "草稿已保存；请通过服务端校验后再发布。";
    } catch (cause) {
      error.value = errorText(cause);
    } finally {
      pending.value = false;
    }
  }

  async function validateDraft() {
    if (!selectedFlow.value || pending.value || loading.value || validating.value) return;
    error.value = "";
    try {
      assertDefinition();
    } catch (cause) {
      error.value = errorText(cause);
      return;
    }
    validating.value = true;
    try {
      const result = await flowApi.validate(selectedFlow.value.flowKey, {
        definitionJson: definition.value,
      });
      if (!result.valid) throw new Error("服务端未通过流程定义校验");
      definition.value = JSON.stringify(
        parseFlowDefinition(result.normalizedDefinitionJson),
        null,
        2,
      );
      validatedDefinition.value = definition.value;
      validationIssues.value = [];
      outcome.value = "流程定义已通过服务端校验；尚未保存或发布。";
    } catch (cause) {
      validatedDefinition.value = "";
      error.value = errorText(cause);
    } finally {
      validating.value = false;
    }
  }

  function assertDefinition() {
    const parsed = assertDraftStructure();
    validationIssues.value = validateStagedFlow(parsed);
    if (validationIssues.value.length) {
      throw new Error(validationIssues.value[0]!.message);
    }
  }

  function assertDraftStructure() {
    const parsed = readStagedFlow(definition.value);
    if (!parsed) throw new Error("流程定义不能为空");
    if (parsed.template !== selectedFlow.value?.modelType) {
      throw new Error("流程定义类型与创建时选择的类型不一致");
    }
    return parsed;
  }

  async function loadModel(template: FlowTemplateType) {
    const model = await flowApi.model(template);
    const actionMap = new Map<string, FlowActionVO>(
      actionCatalog.value.map((action) => [action.code, action]),
    );
    modelNodes.value = (model.definition.nodes || []).map((node) => {
      const action = actionMap.get(node.action);
      return {
        key: node.key,
        label: node.label,
        action: node.action,
        actionLabel: action?.label || node.actionLabel || node.action,
        executorType: action?.executorType || node.executorType || "",
        executorTypeLabel:
          action?.executorTypeLabel || node.executorTypeLabel || "",
        operation: action?.operation || node.operation || "",
      };
    });
  }

  async function publish() {
    if (
      !selectedFlow.value ||
      pending.value ||
      loading.value ||
      dirty.value ||
      !validated.value ||
      version.value?.publishStatus !== "DRAFT"
    )
      return;
    pending.value = true;
    try {
      if (
        !(await confirmAction(
          "确认发布此草稿版本？数据库发布完成不代表运行时已经激活。",
          { confirmText: "发布版本" },
        ))
      )
        return;
      error.value = "";
      const published = await flowApi.publish(selectedFlow.value.flowKey, {
        version: selectedVersion.value,
      });
      outcome.value =
        published.runtimeActivationStatus === "PENDING"
          ? "版本已发布；运行端激活仍待确认。"
          : `版本已发布；运行端状态：${published.runtimeActivationStatus}`;
      await refreshVersions(published.version);
    } catch (cause) {
      error.value = errorText(cause);
    } finally {
      pending.value = false;
    }
  }

  onMounted(reload);
  return {
    flows,
    flowPage,
    flowTotal,
    flowTypes,
    systemModels,
    selectedFlow,
    versions,
    versionPage,
    versionTotal,
    selectedVersion,
    definition,
    version,
    modelNodes,
    loading,
    pending,
    validating,
    createPending,
    error,
    outcome,
    dirty,
    validationIssues,
    validated,
    reload,
    selectFlow,
    selectFlowPage,
    selectVersion,
    selectVersionPage,
    createFlow,
    editVersion,
    saveDraft,
    validateDraft,
    publish,
  };
}
