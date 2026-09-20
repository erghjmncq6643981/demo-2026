import { onBeforeUnmount, ref, watch, type Ref } from 'vue';
import apiClient from '../../../api/apiClient';
import { errorText } from '../../../utils/feedback';
import { type StagedFlow, type StageExecution } from '../model/stagedFlow';
interface ExecutionResponse { instance: { id?: string; snapshot?: string; status?: string }; steps: StageExecution[]; nextCursor: string }
/** 分页读取真实执行事实；关闭弹窗后失效旧请求，不模拟进度。 */
export function useCallExecution(callId: Ref<string>) {
  const flow = ref<StagedFlow | null>(null), executions = ref<StageExecution[]>([]);
  const error = ref(''), loading = ref(false), cursor = ref(''), status = ref(''), version = ref('');
  let generation = 0;
  async function load(more = false) {
    const current = ++generation; loading.value = true; error.value = '';
    if (!more) { flow.value = null; executions.value = []; cursor.value = ''; }
    try {
      const data = await apiClient.get<unknown, ExecutionResponse>(
        `/flow-studio/calls/${encodeURIComponent(callId.value)}`, { params: { after: more ? cursor.value : '0' } });
      if (current !== generation) return;
      if (data.instance.snapshot) {
        const snapshot = JSON.parse(data.instance.snapshot); flow.value = snapshot.definition;
        version.value = `${snapshot.flowKey} · v${snapshot.versionNo}`;
      }
      status.value = data.instance.status || ''; executions.value = more ? [...executions.value, ...data.steps] : data.steps;
      cursor.value = data.nextCursor;
    } catch (cause) { if (current === generation) error.value = errorText(cause); }
    finally { if (current === generation) loading.value = false; }
  }
  watch(callId, () => load(), { immediate: true });
  onBeforeUnmount(() => { generation++; });
  return { flow, executions, error, loading, cursor, status, version, load };
}
