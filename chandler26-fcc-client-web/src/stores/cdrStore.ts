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
  const searchNumber = ref('');
  const searchDirection = ref('');
  const searchStatus = ref('');
  const dateRange = ref<[string, string] | null>(null);
  const startTime = ref('');
  const endTime = ref('');
  const pageInboundCount = ref(0);
  const pageOutboundCount = ref(0);

  async function loadRecords(page: number = 1, size?: number) {
    isLoading.value = true;
    pageNum.value = page;
    if (size) {
      pageSize.value = size;
    }
    try {
      const numQuery = (searchNumber.value || searchCaller.value || '').trim();
      const sTime = startTime.value || (dateRange.value?.[0]) || '';
      const eTime = endTime.value || (dateRange.value?.[1]) || '';

      const params: CdrQueryParams = {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        number: numQuery || undefined,
        direction: searchDirection.value || undefined,
        status: searchStatus.value || undefined,
        startTime: sTime || undefined,
        endTime: eTime || undefined,
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

  function resetFilters() {
    searchNumber.value = '';
    searchCaller.value = '';
    searchDirection.value = '';
    searchStatus.value = '';
    dateRange.value = null;
    startTime.value = '';
    endTime.value = '';
    pageNum.value = 1;
    return loadRecords(1);
  }

  return {
    records,
    total,
    pageNum,
    pageSize,
    isLoading,
    searchCaller,
    searchNumber,
    searchDirection,
    searchStatus,
    dateRange,
    startTime,
    endTime,
    pageInboundCount,
    pageOutboundCount,
    loadRecords,
    resetFilters,
  };
});
