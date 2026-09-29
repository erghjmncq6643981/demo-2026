import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useCdrStore } from './cdrStore';
import * as cdrApi from '../api/cdrApi';

vi.mock('../api/cdrApi', () => ({
  fetchCdrs: vi.fn(),
  getCdrDetail: vi.fn(),
}));

describe('cdrStore filter and pagination tests', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('initializes with default filter and pagination values', () => {
    const store = useCdrStore();
    expect(store.pageNum).toBe(1);
    expect(store.pageSize).toBe(10);
    expect(store.searchNumber).toBe('');
    expect(store.searchDirection).toBe('');
    expect(store.searchStatus).toBe('');
    expect(store.dateRange).toBeNull();
  });

  it('passes all 4 filters (number, direction, status, time range) to fetchCdrs', async () => {
    const store = useCdrStore();
    const mockList = [
      {
        id: '1001',
        bizId: 'call-1001',
        modelType: 'BASIC',
        direction: 'INBOUND' as const,
        caller: '13800000000',
        callee: '1001',
        status: 'ANSWERED',
        initiatedAt: '2026-09-29T10:00:00',
      },
    ];

    vi.mocked(cdrApi.fetchCdrs).mockResolvedValueOnce({
      pageNum: 1,
      pageSize: 20,
      total: 1,
      list: mockList,
    });

    store.searchNumber = '13800000000';
    store.searchDirection = 'INBOUND';
    store.searchStatus = 'ANSWERED';
    store.dateRange = ['2026-09-29T00:00:00', '2026-09-29T23:59:59'];

    await store.loadRecords(1, 20);

    expect(cdrApi.fetchCdrs).toHaveBeenCalledWith({
      pageNum: 1,
      pageSize: 20,
      number: '13800000000',
      direction: 'INBOUND',
      status: 'ANSWERED',
      startTime: '2026-09-29T00:00:00',
      endTime: '2026-09-29T23:59:59',
    });

    expect(store.records).toHaveLength(1);
    expect(store.total).toBe(1);
    expect(store.pageSize).toBe(20);
    expect(store.pageInboundCount).toBe(1);
    expect(store.pageOutboundCount).toBe(0);
  });

  it('resetFilters clears all 4 filters and reloads first page', async () => {
    const store = useCdrStore();
    vi.mocked(cdrApi.fetchCdrs).mockResolvedValue({
      pageNum: 1,
      pageSize: 10,
      total: 0,
      list: [],
    });

    store.searchNumber = '13800000000';
    store.searchCaller = '13800000000';
    store.searchDirection = 'OUTBOUND';
    store.searchStatus = 'NO_ANSWER';
    store.dateRange = ['2026-09-28T00:00:00', '2026-09-28T23:59:59'];
    store.pageNum = 3;

    await store.resetFilters();

    expect(store.searchNumber).toBe('');
    expect(store.searchCaller).toBe('');
    expect(store.searchDirection).toBe('');
    expect(store.searchStatus).toBe('');
    expect(store.dateRange).toBeNull();
    expect(store.pageNum).toBe(1);

    expect(cdrApi.fetchCdrs).toHaveBeenLastCalledWith({
      pageNum: 1,
      pageSize: 10,
      number: undefined,
      direction: undefined,
      status: undefined,
      startTime: undefined,
      endTime: undefined,
    });
  });
});
