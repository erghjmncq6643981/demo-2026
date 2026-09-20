import { computed, onMounted, ref } from "vue";
import { agentApi, type AgentVO } from "../../../api/agentApi";
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
  type DialMode,
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
  const agents = ref<AgentVO[]>([]);
  const attempts = ref<DialAttempt[]>([]);
  const loading = ref(false);
  const attemptsLoading = ref(false);
  const saving = ref(false);
  const controllingId = ref("");
  const error = ref("");
  const page = ref(1);
  const ownerFilter = ref("");
  const createVisible = ref(false);
  const attemptsVisible = ref(false);
  const selectedJob = ref<DialJobSummary | null>(null);
  const form = ref<CreateDialJobReq>({
    owner: "",
    number: "",
    mode: "PROGRESSIVE",
    maxAttempts: 1,
    requestKey: createRequestKey(),
  });
  const hasNext = computed(() => rows.value.length === 50);

  async function load() {
    loading.value = true;
    error.value = "";
    try {
      rows.value = await dialJobManagementApi.list({
        page: page.value,
        owner: ownerFilter.value || undefined,
      });
    } catch (cause) {
      rows.value = [];
      error.value = errorText(cause, "自动外呼任务加载失败");
    } finally {
      loading.value = false;
    }
  }

  async function loadAgents() {
    try {
      const result = await agentApi.list({
        pageNum: 1,
        pageSize: 200,
        status: "ENABLED",
      });
      agents.value = result.list || [];
    } catch {
      agents.value = [];
    }
  }

  function search() {
    page.value = 1;
    void load();
  }

  function openCreate() {
    form.value = {
      owner: "",
      number: "",
      mode: "PROGRESSIVE",
      maxAttempts: 1,
      requestKey: createRequestKey(),
    };
    createVisible.value = true;
  }

  async function create() {
    if (!form.value.owner || !form.value.number.trim()) {
      error.value = "执行坐席和被叫号码不能为空";
      return;
    }
    saving.value = true;
    error.value = "";
    try {
      await dialJobManagementApi.create({
        ...form.value,
        number: form.value.number.trim(),
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
    void Promise.all([load(), loadAgents()]);
  });

  return {
    rows,
    agents,
    attempts,
    loading,
    attemptsLoading,
    saving,
    controllingId,
    error,
    page,
    ownerFilter,
    createVisible,
    attemptsVisible,
    selectedJob,
    form,
    hasNext,
    load,
    search,
    openCreate,
    create,
    showAttempts,
    control,
    previous,
    next,
  };
}
