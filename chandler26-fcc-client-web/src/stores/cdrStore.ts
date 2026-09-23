import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { CallCdrItem, CdrQueryParams } from '../types/cdr';
import { fetchCdrs } from '../api/cdrApi';

export const useCdrStore = defineStore('cdr', () => {
  const records = ref<CallCdrItem[]>([]);
  const total = ref(0);
  const pageNum = ref(1);
  const pageSize = ref(10);
  const isLoading = ref(false);
  const searchCaller = ref('');
  const searchDirection = ref('');
  const pageInboundCount = ref(0);
  const pageOutboundCount = ref(0);

  async function loadRecords(page: number = 1) {
    isLoading.value = true;
    pageNum.value = page;
    try {
      const params: CdrQueryParams = {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        caller: searchCaller.value || undefined,
        direction: searchDirection.value || undefined,
      };
      const res = await fetchCdrs(params);
      records.value = res.list || [];
      total.value = res.total || 0;

      // Only summarize the loaded page; the list API does not return daily aggregates.
      let inCount = 0;
      let outCount = 0;
      records.value.forEach((r) => {
        if (r.direction === 'INBOUND') inCount++;
        if (r.direction === 'OUTBOUND') outCount++;
      });
      pageInboundCount.value = inCount;
      pageOutboundCount.value = outCount;
    } catch (e) {
      console.error('Failed to load CDRs from admin backend:', e);
      records.value = [];
      total.value = 0;
      pageInboundCount.value = 0;
      pageOutboundCount.value = 0;
    } finally {
      isLoading.value = false;
    }
  }

  return {
    records,
    total,
    pageNum,
    pageSize,
    isLoading,
    searchCaller,
    searchDirection,
    pageInboundCount,
    pageOutboundCount,
    loadRecords,
  };
});
