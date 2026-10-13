import { expect, it, vi } from 'vitest';
import { OwnedCallback } from './ownedCallback';

it('only the current subscription can clear its callback', () => {
  const slot = new OwnedCallback<string>();
  const old = vi.fn(), current = vi.fn();
  const stopOld = slot.subscribe(old);
  const stopCurrent = slot.subscribe(current);
  stopOld();
  slot.emit('connected');
  expect(old).not.toHaveBeenCalled();
  expect(current).toHaveBeenCalledWith('connected');
  stopCurrent();
  stopCurrent();
  slot.emit('ended');
  expect(current).toHaveBeenCalledOnce();
});

it('protects a replacement even when the same function is reused', () => {
  const slot = new OwnedCallback<string>();
  const callback = vi.fn();
  const stopOld = slot.subscribe(callback);
  slot.subscribe(callback);
  stopOld();
  slot.emit('ready');
  expect(callback).toHaveBeenCalledOnce();
});
