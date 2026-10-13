/** Fold persisted step boundaries in their existing order; malformed JSON contributes no facts. */
export function collectExecutionFacts(steps: ReadonlyArray<{ input?: string; output?: string }>): Record<string, any> {
  const facts: Record<string, any> = {};
  for (const step of steps) {
    for (const boundary of [step.output, step.input]) {
      if (!boundary) continue;
      try { Object.assign(facts, JSON.parse(boundary)); } catch { /* Raw payload remains available in detail. */ }
    }
  }
  return facts;
}
