import { ref } from 'vue';
import { telephonyApi } from '../../api/apiClient';
interface Job { id: string; number: string; mode: string; status: string; attempts?: Array<{ id: string; attemptNo: number; status: string; result?: string }> }
/** Own task requests, stable creation keys and item-level outcomes. */
export function useDialJobs() {
  const rows = ref<Job[]>([]), selected = ref<Job | null>(null), number = ref(''), mode = ref('PROGRESSIVE'), maxAttempts = ref(1), page = ref(1), error = ref(''), busy = ref(false);
  let requestKey = crypto.randomUUID();
  const run = async (action: () => Promise<void>) => {
    if (busy.value) return;
    busy.value = true; error.value = '';
    try { await action(); } catch (failure) { error.value = failure instanceof Error ? failure.message : '任务操作失败'; }
    finally { busy.value = false; }
  };
  const read = async () => { rows.value = (await telephonyApi.get<unknown, { data: Job[] }>('/dial-jobs', { params: { page: page.value } })).data; };
  const load = () => run(read);
  const create = () => run(async () => {
    await telephonyApi.post('/dial-jobs', { number: number.value, mode: mode.value, maxAttempts: maxAttempts.value, requestKey });
    requestKey = crypto.randomUUID(); number.value = ''; await read();
  });
  const detail = (id: string) => run(async () => { selected.value = (await telephonyApi.get<unknown, { data: Job }>(`/dial-jobs/${encodeURIComponent(id)}`)).data; });
  const control = (id: string, action: string) => run(async () => { await telephonyApi.post(`/dial-jobs/${encodeURIComponent(id)}/state`, { action }); await read(); });
  return { rows, selected, number, mode, maxAttempts, page, error, busy, load, create, detail, control };
}
