import { expect, it } from 'vitest';
import { currentBusinessDate } from './businessDate';

it('uses the Beijing calendar date across UTC midnight boundaries', () => {
  const date = currentBusinessDate(new Date('2026-10-03T17:00:00Z'));
  expect([date.getFullYear(), date.getMonth() + 1, date.getDate()]).toEqual([2026, 10, 4]);
});
