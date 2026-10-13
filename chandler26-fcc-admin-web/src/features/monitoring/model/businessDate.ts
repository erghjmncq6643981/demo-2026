/** Return a local calendar Date representing today's Beijing business date, not the browser's timezone date. */
export function currentBusinessDate(now: Date = new Date()): Date {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: 'numeric', day: 'numeric',
  }).formatToParts(now);
  const value = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find(part => part.type === type)?.value);
  return new Date(value('year'), value('month') - 1, value('day'));
}
