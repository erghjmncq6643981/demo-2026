/** Validate the editor transport boundary without claiming runtime validation. */
export function parseFlowDefinition(source: string): Record<string, unknown> {
  let value: unknown;
  try { value = JSON.parse(source); }
  catch { throw new Error('流程定义不是合法 JSON'); }
  if (value === null || Array.isArray(value) || typeof value !== 'object') {
    throw new Error('流程定义必须是 JSON 对象');
  }
  return value as Record<string, unknown>;
}
