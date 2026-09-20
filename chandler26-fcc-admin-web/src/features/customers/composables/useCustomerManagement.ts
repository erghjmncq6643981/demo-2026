import { computed, onMounted, ref } from "vue";
import { agentApi, type AgentVO } from "../../../api/agentApi";
import { errorText, toastSuccess } from "../../../utils/feedback";
import {
  customerManagementApi,
  type CustomerDetail,
  type CustomerSummary,
  type SaveCustomerReq,
} from "../api/customerManagementApi";

const emptyForm = (): SaveCustomerReq & { owner: string; id: string } => ({
  id: "",
  owner: "",
  name: "",
  phoneNumber: "",
  companyName: "",
  notes: "",
});

/** 管理客户列表、详情和编辑状态，视图只负责呈现。 */
export function useCustomerManagement() {
  const rows = ref<CustomerSummary[]>([]);
  const agents = ref<AgentVO[]>([]);
  const loading = ref(false);
  const saving = ref(false);
  const error = ref("");
  const page = ref(1);
  const ownerFilter = ref("");
  const phoneFilter = ref("");
  const dialogVisible = ref(false);
  const detailLoading = ref(false);
  const form = ref(emptyForm());
  const editing = computed(() => Boolean(form.value.id));
  const hasNext = computed(() => rows.value.length === 50);

  async function load() {
    loading.value = true;
    error.value = "";
    try {
      rows.value = await customerManagementApi.list({
        page: page.value,
        owner: ownerFilter.value || undefined,
        phone: phoneFilter.value.trim() || undefined,
      });
    } catch (cause) {
      rows.value = [];
      error.value = errorText(cause, "客户资料加载失败");
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

  function reset() {
    ownerFilter.value = "";
    phoneFilter.value = "";
    search();
  }

  function create() {
    form.value = emptyForm();
    dialogVisible.value = true;
  }

  async function edit(row: CustomerSummary) {
    dialogVisible.value = true;
    detailLoading.value = true;
    form.value = { ...emptyForm(), id: row.id, owner: row.owner };
    try {
      const detail: CustomerDetail = await customerManagementApi.detail(row.id);
      form.value = {
        ...emptyForm(),
        ...detail,
        id: detail.id,
        owner: row.owner,
        version: detail.version,
      };
    } catch (cause) {
      error.value = errorText(cause, "客户详情加载失败");
      dialogVisible.value = false;
    } finally {
      detailLoading.value = false;
    }
  }

  async function save() {
    if (
      !form.value.name.trim() ||
      !form.value.phoneNumber.trim() ||
      (!editing.value && !form.value.owner)
    ) {
      error.value = "客户姓名、号码和负责坐席不能为空";
      return;
    }
    saving.value = true;
    error.value = "";
    try {
      const payload: SaveCustomerReq = {
        name: form.value.name.trim(),
        phoneNumber: form.value.phoneNumber.trim(),
        companyName: form.value.companyName?.trim(),
        notes: form.value.notes?.trim(),
        version: form.value.version,
      };
      if (editing.value)
        await customerManagementApi.update(form.value.id, payload);
      else await customerManagementApi.create(form.value.owner, payload);
      dialogVisible.value = false;
      toastSuccess(editing.value ? "客户资料已更新" : "客户资料已创建");
      await load();
    } catch (cause) {
      error.value = errorText(cause, "客户资料保存失败");
    } finally {
      saving.value = false;
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
    loading,
    saving,
    error,
    page,
    ownerFilter,
    phoneFilter,
    dialogVisible,
    detailLoading,
    form,
    editing,
    hasNext,
    load,
    search,
    reset,
    create,
    edit,
    save,
    previous,
    next,
  };
}
