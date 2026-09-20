const { test } = require('node:test');
const assert = require('node:assert/strict');
const { WebSocketServer } = require('ws');
const { Realtime } = require('./realtime.cjs');

test('desktop owns one authenticated socket and stops reconnect on logout', async () => {
  const server = new WebSocketServer({ port: 0, host: '127.0.0.1', handleProtocols: protocols => protocols.has('fcc-agent') && 'fcc-agent' });
  await new Promise(resolve => server.once('listening', resolve));
  const states = [];
  let delivered;
  const received = new Promise(resolve => { delivered = resolve; });
  const client = new Realtime(state => states.push(state), message => delivered(message));
  server.once('connection', (socket, request) => {
    assert.equal(request.headers.origin, 'https://fcc.example');
    assert.match(request.headers['sec-websocket-protocol'], /auth\./);
    socket.send(JSON.stringify({ type: 'SCREEN_POP', callId: 'long-id' }));
  });
  client.connect(`ws://127.0.0.1:${server.address().port}`, 'test-only-token', 'https://fcc.example');
  try {
    assert.equal((await received).callId, 'long-id');
    assert.ok(states.includes('CONNECTED'));
    client.disconnect();
    assert.equal(states.at(-1), 'DISCONNECTED');
  } finally { client.disconnect(); for (const socket of server.clients) socket.terminate(); await new Promise(resolve => server.close(resolve)); }
});
