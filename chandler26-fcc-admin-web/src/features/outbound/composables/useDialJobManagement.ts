import { computed, onMounted, ref } from "vue";
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
  const attempts = ref<DialAttempt[]>([]);
  const loading = ref(false);
  const attemptsLoading = ref(false);
  const saving = ref(false);
  const controllingId = ref("");
  const error = ref("");
  const page = ref(1);
  const pageSize = ref(10);
  const total = ref(0);
  const searchNumber = ref("");
  const searchTriggerSource = ref("");
  const searchStatus = ref("");
  const dateRange = ref<[string, string] | null>(null);
  const createVisible = ref(false);
  const attemptsVisible = ref(false);
  const selectedJob = ref<DialJobSummary | null>(null);
  const form = ref<CreateDialJobReq>({
    taskType: "NOTIFY",
    number: "",
    text: "",
    confirmDigit: "1",
    timeoutSeconds: 10,
    bizId: "",
    maxAttempts: 1,
    requestKey: createRequestKey(),
  });
  const hasNext = computed(() => page.value * pageSize.value < total.value);

  async function load() {
    loading.value = true;
    error.value = "";
    try {
      const result = await dialJobManagementApi.list({
        page: page.value,
        pageSize: pageSize.value,
        number: searchNumber.value.trim() || undefined,
        triggerSource: searchTriggerSource.value || undefined,
        status: searchStatus.value || undefined,
        startTime: dateRange.value?.[0] || undefined,
        endTime: dateRange.value?.[1] || undefined,
      });
      if (Array.isArray(result)) {
        rows.value = result;
        total.value = result.length;
      } else if (result && typeof result === "object") {
        rows.value = Array.isArray(result.list) ? result.list : [];
        total.value = typeof result.total === "number" ? result.total : rows.value.length;
      } else {
        rows.value = [];
        total.value = 0;
      }
    } catch (cause) {
      rows.value = [];
      total.value = 0;
      error.value = errorText(cause, "自动外呼任务加载失败");
    } finally {
      loading.value = false;
    }
  }

  function search() {
    page.value = 1;
    void load();
  }

  function reset() {
    searchNumber.value = "";
    searchTriggerSource.value = "";
    searchStatus.value = "";
    dateRange.value = null;
    page.value = 1;
    void load();
  }

  function handlePageChange(newPage: number) {
    page.value = newPage;
    void load();
  }

  function handleSizeChange(newSize: number) {
    pageSize.value = newSize;
    page.value = 1;
    void load();
  }

  function openCreate() {
    form.value = {
      taskType: "NOTIFY",
      number: "",
      text: "",
      confirmDigit: "1",
      timeoutSeconds: 10,
      bizId: "",
      maxAttempts: 1,
      requestKey: createRequestKey(),
    };
    createVisible.value = true;
  }

  async function create() {
    const text = form.value.text.trim();
    if (!form.value.number.trim() || !text) {
      error.value = "被叫号码和文案不能为空";
      return;
    }
    saving.value = true;
    error.value = "";
    try {
      const payload: CreateDialJobReq = {
        taskType: form.value.taskType,
        number: form.value.number.trim(),
        text,
        maxAttempts: 1,
        requestKey: form.value.requestKey,
        bizId: form.value.bizId?.trim() || undefined,
      };
      if (form.value.taskType === "SURVEY") {
        payload.confirmDigit = form.value.confirmDigit || "1";
        payload.timeoutSeconds = form.value.timeoutSeconds || 10;
      }
      await dialJobManagementApi.create(payload);
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
    void load();
  });

  return {
    rows,
    attempts,
    loading,
    attemptsLoading,
    saving,
    controllingId,
    error,
    page,
    pageSize,
    total,
    searchNumber,
    searchTriggerSource,
    searchStatus,
    dateRange,
    createVisible,
    attemptsVisible,
    selectedJob,
    form,
    hasNext,
    load,
    search,
    reset,
    handlePageChange,
    handleSizeChange,
    openCreate,
    create,
    showAttempts,
    control,
    previous,
    next,
  };
}
