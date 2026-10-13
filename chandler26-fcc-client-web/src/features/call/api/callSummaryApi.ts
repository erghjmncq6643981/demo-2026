import { telephonyApi } from '../../../api/apiClient';

/** Submit a summary through the business REST adapter; identifiers remain opaque strings. */
export function submitCallSummary(callId: string, summary: { category: string; intent: string; notes: string }) {
  return telephonyApi.post(`/calls/${encodeURIComponent(callId)}/summary`, summary);
}
