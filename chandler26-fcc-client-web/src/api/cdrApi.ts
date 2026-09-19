import { adminApi } from './apiClient';
import type { CdrPageResult, CdrQueryParams, CallCdrItem } from '../types/cdr';

interface CommonResult<T> {
  code: number;
  message: string;
  data: T;
}

/**
 * 分页检索真实 MySQL 话单数据
 */
export async function fetchCdrs(params: CdrQueryParams): Promise<CdrPageResult> {
  const res = (await adminApi.get('/cdrs', { params })) as unknown as CommonResult<CdrPageResult>;
  return res.data;
}

/**
 * 获取单个话单与信道 Leg 详情
 */
export async function getCdrDetail(id: string): Promise<CallCdrItem> {
  const res = (await adminApi.get(`/cdrs/${id}`)) as unknown as CommonResult<CallCdrItem>;
  return res.data;
}
