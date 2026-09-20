import { ref } from 'vue';
import { telephonyApi } from '../../api/apiClient';
import { useAgentStore } from '../../stores/agentStore';

/** Binding proof is performed on the physical phone; this coordinator only requests and checks it. */
export function usePhoneBinding() {
  const extension = ref(''), code = ref(''), status = ref(''), error = ref(''), busy = ref(false);
  const agent = useAgentStore();
  async function run(action: () => Promise<void>) {
    if (busy.value) return;
    busy.value = true; error.value = '';
    try { await action(); } catch (failure) { error.value = failure instanceof Error ? failure.message : '绑定失败'; }
    finally { busy.value = false; }
  }
  const create = () => run(async () => {
    const response = await telephonyApi.post<unknown, { data: { code: string } }>('/phone-binding', { extension: extension.value });
    code.value = response.data.code; status.value = 'PENDING';
  });
  const refresh = () => run(async () => {
    const response = await telephonyApi.get<unknown, { data: { status: string } }>('/phone-binding');
    status.value = response.data.status;
    if (status.value !== 'PENDING') code.value = '';
    if (status.value === 'CONSUMED') await agent.loadEndpoints();
  });
  return { extension, code, status, error, busy, create, refresh };
}
