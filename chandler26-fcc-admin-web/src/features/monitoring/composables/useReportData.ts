import { ref, shallowRef, onScopeDispose } from 'vue';
import type { CallCdrVO } from '../../../api/cdrApi';
import { errorText } from '../../../utils/feedback';

/** Owns report requests; a failed or superseded request cannot masquerade as zero or old data. */
export function useReportData<T = CallCdrVO>(fetchRows: () => Promise<T[]>) {
  const rawCdrs = shallowRef<T[]>([]);
  const isLoading = ref(false), hasLoaded = ref(false), loadError = ref('');
  const refreshTime = ref('尚未加载');
  let generation = 0;
  async function loadData() {
    const current = ++generation;
    isLoading.value = true;
    hasLoaded.value = false;
    loadError.value = '';
    rawCdrs.value = [];
    try {
      const rows = await fetchRows();
      if (current !== generation) return;
      rawCdrs.value = rows;
      hasLoaded.value = true;
      refreshTime.value = new Date().toLocaleTimeString();
    } catch (cause) {
      if (current === generation) loadError.value = errorText(cause);
    } finally {
      if (current === generation) isLoading.value = false;
    }
  }
  onScopeDispose(() => { generation++; });
  return { rawCdrs, isLoading, hasLoaded, loadError, refreshTime, loadData };
}
