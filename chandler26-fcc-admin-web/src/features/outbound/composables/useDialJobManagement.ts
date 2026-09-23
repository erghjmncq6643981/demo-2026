import { computed, onMounted, ref } from "vue";
import { flowApi, type FlowDefinitionVO } from "../../../api/flowApi";
import {
  confirmAction,
  errorText,
  toastSuccess,
} from "../../../utils/feedback";
import {
  dialJobManagementApi,
  type CreateDialJobReq,
  type DialAttempt,
  type DialJobAction,
  type DialJobSummary,
} from "../api/dialJobManagementApi";

const createRequestKey = () => {
  const random =
    globalThis.crypto?.randomUUID?.() ||
    `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `admin-${random}`;
};

/** 管理自动外呼任务、逐次结果和受控状态操作。 */
export function useDialJobManagement() {
  const rows = ref<DialJobSummary[]>([]);
  const flows = ref<FlowDefinitionVO[]>([]);
  const flowsLoading = ref(false);
  const flowsError = ref("");
  const attempts = ref<DialAttempt[]>([]);
  const loading = ref(false);
  const attemptsLoading = ref(false);
  const saving = ref(false);
  const controllingId = ref("");
  const error = ref("");
  const page = ref(1);
  const flowFilter = ref("");
  const createVisible = ref(false);
  const attemptsVisible = ref(false);
  const selectedJob = ref<DialJobSummary | null>(null);
  const form = ref<CreateDialJobReq>({
    number: "",
    flowKey: "",
    variables: { text: "", confirmDigit: "1" },
    maxAttempts: 1,
    requestKey: createRequestKey(),
  });
  const hasNext = computed(() => rows.value.length === 50);

  async function load() {
    loading.value = true;
    error.value = "";
    try {
      const result = await dialJobManagementApi.list({
        page: page.value,
        flowKey: flowFilter.value || undefined,
      });
      rows.value = Array.isArray(result) ? result : [];
    } catch (cause) {
      rows.value = [];
      error.value = errorText(cause, "自动外呼任务加载失败");
    } finally {
      loading.value = false;
    }
  }

  async function loadFlows() {
    flowsLoading.value = true;
    flowsError.value = "";
    try {
      const result = await flowApi.list({
        pageNum: 1,
        pageSize: 100,
      });
      flows.value = (result.list || []).filter(
        (flow) =>
          flow.status === "PUBLISHED"
          && ["NOTIFICATION", "AUTO_DIAL", "AUTO_DIAL_NOTIFICATION"].includes(flow.modelType),
      );
    } catch (cause) {
      flows.value = [];
      flowsError.value = errorText(cause, "已发布自动外呼流程加载失败");
    } finally {
      flowsLoading.value = false;
    }
  }

  function search() {
    page.value = 1;
    void load();
  }

  function openCreate() {
    form.value = {
      number: "",
      flowKey: flows.value[0]?.flowKey || "",
      variables: { text: "", confirmDigit: "1" },
      maxAttempts: 1,
      requestKey: createRequestKey(),
    };
    createVisible.value = true;
  }

  async function create() {
    const text = String(form.value.variables.text || "").trim();
    if (!form.value.flowKey || !form.value.number.trim() || !text) {
      error.value = "自动外呼流程、被叫号码和播报文案不能为空";
      return;
    }
    saving.value = true;
    error.value = "";
    try {
      await dialJobManagementApi.create({
        ...form.value,
        number: form.value.number.trim(),
        variables: {
          ...form.value.variables,
          text,
        },
      });
      createVisible.value = false;
      toastSuccess("自动外呼任务已进入持久调度队列");
      await load();
    } catch (cause) {
      error.value = errorText(cause, "自动外呼任务创建失败");
    } finally {
      saving.value = false;
    }
  }

  async function showAttempts(row: DialJobSummary) {
    selectedJob.value = row;
    attempts.value = [];
    attemptsVisible.value = true;
    attemptsLoading.value = true;
    try {
      attempts.value = await dialJobManagementApi.attempts(row.id);
    } catch (cause) {
      error.value = errorText(cause, "外呼尝试记录加载失败");
    } finally {
      attemptsLoading.value = false;
    }
  }

  async function control(row: DialJobSummary, action: DialJobAction) {
    const label =
      action === "PAUSE" ? "暂停" : action === "RESUME" ? "恢复" : "取消";
    const accepted = await confirmAction(
      action === "CANCEL"
        ? "取消任务只阻止后续调度，不会强制挂断已经建立的通话。确认取消吗？"
        : `确认${label}任务 ${row.id} 吗？`,
      {
        title: `${label}自动外呼任务`,
        confirmText: label,
        danger: action === "CANCEL",
      },
    );
    if (!accepted) return;
    controllingId.value = row.id;
    error.value = "";
    try {
      await dialJobManagementApi.control(row.id, action);
      toastSuccess(`任务${label}指令已受理`);
      await load();
    } catch (cause) {
      error.value = errorText(cause, `任务${label}失败`);
    } finally {
      controllingId.value = "";
    }
  }

  function previous() {
    if (page.value > 1) {
      page.value--;
      void load();
    }
  }

  function next() {
    if (hasNext.value) {
      page.value++;
      void load();
    }
  }

  onMounted(() => {
    void Promise.all([load(), loadFlows()]);
  });

  return {
    rows,
    flows,
    flowsLoading,
    flowsError,
    attempts,
    loading,
    attemptsLoading,
    saving,
    controllingId,
    error,
    page,
    flowFilter,
    createVisible,
    attemptsVisible,
    selectedJob,
    form,
    hasNext,
    load,
    loadFlows,
    search,
    openCreate,
    create,
    showAttempts,
    control,
    previous,
    next,
  };
}
