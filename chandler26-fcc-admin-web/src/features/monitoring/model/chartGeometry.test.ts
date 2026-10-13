import { expect, it } from 'vitest';
import { buildSmoothPath, buildAreaPath } from './chartGeometry';

it('keeps empty and one-point reports renderable', () => {
  expect(buildSmoothPath([])).toBe('');
  expect(buildAreaPath([], 160)).toBe('');
  expect(buildSmoothPath([{ x: 50, y: 160 }])).toBe('M 50,160');
});

it('preserves smooth interpolation and the closed area baseline', () => {
  const points = [{ x: 50, y: 160 }, { x: 150, y: 80 }];
  expect(buildSmoothPath(points)).toBe('M 50,160 C 80.0,160.0 120.0,80.0 150.0,80.0');
  expect(buildAreaPath(points, 160)).toBe('M 50,160 C 80.0,160.0 120.0,80.0 150.0,80.0 L 150,160 L 50,160 Z');
});
