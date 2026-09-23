import { describe, expect, it } from 'vitest';
import { buildEndpointOptions, endpointKey, parseEndpointKey } from './endpointSelection';

describe('endpointSelection', () => {
  it('builds choices only from verified endpoint facts', () => {
    const options = buildEndpointOptions({
      workNo: '901001',
      webrtcWorkNo: '901001',
      sipExtensions: ['1007', '1007', '1008'],
      mobilePhone: '',
    });

    expect(options.map((item) => item.key)).toEqual([
      'WEBRTC:901001',
      'SIP:1007',
      'SIP:1008',
    ]);
  });

  it('keeps endpoint values opaque while parsing a selection', () => {
    const value = '09010010000000000001';
    expect(parseEndpointKey(endpointKey('SIP', value))).toEqual({ type: 'SIP', value });
    expect(parseEndpointKey('UNKNOWN:1007')).toBeNull();
  });
});
