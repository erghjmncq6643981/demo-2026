import { ref } from 'vue';
import { telephonyApi } from '../../api/apiClient';

export interface Customer {
  id?: string; name: string; phoneNumber: string; companyName?: string; notes?: string; version?: number;
}
/** Own loading, detail retrieval and version-aware customer writes outside views. */
export function useCustomers() {
  const rows = ref<Customer[]>([]);
  const selected = ref<Customer>({ name: '', phoneNumber: '' });
  const page = ref(1), busy = ref(false), error = ref('');
  async function run(action: () => Promise<void>) {
    if (busy.value) return;
    busy.value = true; error.value = '';
    try { await action(); } catch (failure) { error.value = failure instanceof Error ? failure.message : '客户资料操作失败'; }
    finally { busy.value = false; }
  }
  const load = () => run(async () => {
    const response = await telephonyApi.get<unknown, { data: Customer[] }>('/customers', { params: { page: page.value } });
    rows.value = response.data;
  });
  const detail = (id: string) => run(async () => {
    const response = await telephonyApi.get<unknown, { data: Customer }>(`/customers/${encodeURIComponent(id)}`);
    selected.value = response.data;
  });
  const save = () => run(async () => {
    const customer = selected.value;
    const response = customer.id
      ? await telephonyApi.put<unknown, { data: { id: string } }>(`/customers/${encodeURIComponent(customer.id)}`, customer)
      : await telephonyApi.post<unknown, { data: { id: string } }>('/customers', customer);
    const refreshed = await telephonyApi.get<unknown, { data: Customer }>(`/customers/${encodeURIComponent(response.data.id)}`);
    selected.value = refreshed.data;
    const list = await telephonyApi.get<unknown, { data: Customer[] }>('/customers', { params: { page: page.value } });
    rows.value = list.data;
  });
  return { rows, selected, page, busy, error, load, detail, save };
}
