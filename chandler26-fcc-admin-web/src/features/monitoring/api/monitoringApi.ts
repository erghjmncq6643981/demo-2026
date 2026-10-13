import apiClient from '../../../api/apiClient';

/** Compact aggregate buckets, never individual CDRs or opaque identifiers. */
export interface MonitoringBucket {
  initiatedAt: string;
  direction: string;
  automatic: boolean;
  total: number;
  connected: number;
  talkDurationMs: number;
}

/** Normalize numeric metrics at the adapter; Java Long serialization never changes ID semantics. */
export async function fetchMonitoring(period: string, date: string): Promise<MonitoringBucket[]> {
  const rows = await apiClient.get<unknown, MonitoringBucket[]>('/monitoring', { params: { period, date } });
  return rows.map(row => ({ ...row, total: Number(row.total), connected: Number(row.connected), talkDurationMs: Number(row.talkDurationMs) }));
}
