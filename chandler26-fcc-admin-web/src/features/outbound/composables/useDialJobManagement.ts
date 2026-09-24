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
  const triggerSourceFilter = ref("");
  const createVisible = ref(false);
  const attemptsVisible = ref(false);
  const selectedJob = ref<DialJobSummary | null>(null);
  const form = ref<CreateDialJobReq>({
    number: "",
    text: "",
    confirmDigit: "1",
    timeoutSeconds: 10,
    bizId: "",
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
        triggerSource: triggerSourceFilter.value || undefined,
      });
      rows.value = Array.isArray(result) ? result : [];
    } catch (cause) {
      rows.value = [];
      error.value = errorText(cause, "自动外呼任务加载失败");
    } finally {
      loading.value = false;
    }
  }

  function search() {
    page.value = 1;
    void load();
  }

  function openCreate() {
    form.value = {
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
      error.value = "被叫号码和通知文案不能为空";
      return;
    }
    saving.value = true;
    error.value = "";
    try {
      await dialJobManagementApi.create({
        ...form.value,
        number: form.value.number.trim(),
        text,
        bizId: form.value.bizId?.trim() || undefined,
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
    triggerSourceFilter,
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
