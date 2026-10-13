import { effectScope } from 'vue';
import { expect, it, vi } from 'vitest';
import { fetchMonitoring } from '../api/monitoringApi';
import { useMonitoringReport } from './useMonitoringReport';

vi.mock('../api/monitoringApi', () => ({ fetchMonitoring: vi.fn() }));

it('sums weighted aggregate buckets above 2000 without counting buckets as calls', async () => {
  vi.mocked(fetchMonitoring).mockResolvedValue([
    { initiatedAt: '2026-10-04T10:00:00', direction: 'OUTBOUND', automatic: false, total: 2501, connected: 2001, talkDurationMs: 60000 },
    { initiatedAt: '2026-10-04T10:00:00', direction: 'OUTBOUND', automatic: true, total: 500, connected: 100, talkDurationMs: 30000 },
    { initiatedAt: '2026-10-04T11:00:00', direction: 'INBOUND', automatic: false, total: 10, connected: 2, talkDurationMs: 1000 },
  ]);
  const scope = effectScope();
  const report = scope.run(() => useMonitoringReport())!;
  report.currentDate.value = new Date(2026, 9, 4);
  await report.loadData();
  expect(fetchMonitoring).toHaveBeenCalledWith('day', '2026-10-04');
  expect(report.outboundTotal.value).toBe(3001);
  expect(report.outboundConnected.value).toBe(2101);
  expect(report.inboundTotal.value).toBe(10);
  expect(report.autoTotal.value).toBe(500);
  expect(report.currentTrendStats.value.count).toBe(3011);
  expect(report.trendBuckets.value[10]?.total).toBe(3001);
  expect(report.trendBuckets.value[10]?.connected).toBe(2101);
  report.selectedType.value = 'auto';
  expect(report.currentTrendStats.value.count).toBe(500);
  scope.stop();
});
