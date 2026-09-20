import { computed, onMounted, ref } from "vue";
import {
  flowApi,
  type FlowActionVO,
  type FlowDefinitionVO,
  type FlowVersionVO,
} from "../../../api/flowApi";
import type { FlowModelNode } from "../model/stagedFlow";
import { confirmAction, errorText } from "../../../utils/feedback";
import { parseFlowDefinition } from "../model/flowDefinition";

/** Owns backend flow/version selection and explicit draft/publication outcomes. */
export function useFlowEditor() {
  const flows = ref<FlowDefinitionVO[]>([]);
  const flowPage = ref(1);
  const flowTotal = ref(0);
  const selectedFlow = ref<FlowDefinitionVO | null>(null);
  const versions = ref<FlowVersionVO[]>([]);
  const versionPage = ref(1);
  const versionTotal = ref(0);
  const selectedVersion = ref("");
  const definition = ref("");
  const savedDefinition = ref("");
  const loading = ref(false);
  const pending = ref(false);
  const error = ref("");
  const outcome = ref("");
  const modelNodes = ref<FlowModelNode[]>([]);
  const dirty = computed(() => definition.value !== savedDefinition.value);
  const version = computed(() =>
    versions.value.find((item) => item.version === selectedVersion.value),
  );

  function applyDefinition(record?: FlowVersionVO) {
    selectedVersion.value = record?.version || "";
    definition.value = record?.definitionJson || "";
    savedDefinition.value = definition.value;
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
      await refreshVersions();
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
      const [flowResult, actions, model] = await Promise.all([
        flowApi.list({ pageNum: flowPage.value, pageSize: 20 }),
        flowApi.actions(),
        flowApi.model("INBOUND"),
      ]);
      flows.value = flowResult.list;
      flowTotal.value = flowResult.total;
      const actionMap = new Map<string, FlowActionVO>(
        actions.map((action) => [action.code, action]),
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
      selectedFlow.value =
        flows.value.find(
          (item) => item.flowKey === selectedFlow.value?.flowKey,
        ) ||
        flows.value[0] ||
        null;
      versions.value = [];
      applyDefinition();
      await refreshVersions();
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

  async function saveDraft() {
    if (!selectedFlow.value || pending.value || loading.value) return;
    error.value = "";
    try {
      parseFlowDefinition(definition.value);
    } catch (cause) {
      error.value = errorText(cause);
      return;
    }
    pending.value = true;
    try {
      const saved = await flowApi.saveDraft(selectedFlow.value.flowKey, {
        definitionJson: definition.value,
      });
      savedDefinition.value = definition.value;
      outcome.value = "草稿已保存，尚未发布。";
      versionPage.value = 1;
      await refreshVersions(saved.version);
    } catch (cause) {
      error.value = errorText(cause);
    } finally {
      pending.value = false;
    }
  }

  async function publish() {
    if (
      !selectedFlow.value ||
      pending.value ||
      loading.value ||
      dirty.value ||
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
    error,
    outcome,
    dirty,
    reload,
    selectFlow,
    selectFlowPage,
    selectVersion,
    selectVersionPage,
    saveDraft,
    publish,
  };
}
