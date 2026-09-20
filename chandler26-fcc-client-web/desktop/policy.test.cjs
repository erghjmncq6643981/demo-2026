const { test } = require('node:test');
const assert = require('node:assert/strict');
const { deploymentUrl, socketUrl, ringingEvent } = require('./policy.cjs');
test('reject insecure remote deployment and credential-bearing URLs', () => {
  for (const url of ['http://example.com', 'https://user:password@example.com', 'file:///tmp/a', 'https://example.com?token=x']) assert.throws(() => deploymentUrl(url));
  assert.equal(deploymentUrl('http://127.0.0.1:8888').host, '127.0.0.1:8888');
});
test('pin websocket destination to trusted deployment', () => {
  const url = deploymentUrl('https://fcc.example.com');
  assert.equal(socketUrl(url, 'wss://fcc.example.com/ws/agent'), 'wss://fcc.example.com/ws/agent');
  assert.throws(() => socketUrl(url, 'wss://attacker.example/ws/agent'));
});
test('ignore expired, malformed or future screen-pop events', () => {
  const event = { type: 'SCREEN_POP', callId: '9007199254740993', timestamp: 1000 };
  assert.equal(ringingEvent(event, 2000).callId, event.callId);
  assert.equal(ringingEvent(event, 50000), null);
  assert.equal(ringingEvent({ ...event, timestamp: 100000 }, 2000), null);
  assert.equal(ringingEvent({ ...event, callId: 123 }, 2000), null);
});
