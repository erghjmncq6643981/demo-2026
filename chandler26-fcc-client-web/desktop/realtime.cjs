'use strict';
const WebSocket = require('ws');

/** One main-process connection survives hidden windows, with bounded reconnect and heartbeat. */
class Realtime {
  constructor(onState, onMessage) {
    this.onState = onState;
    this.onMessage = onMessage;
    this.generation = 0;
    this.attempt = 0;
  }

  connect(url, token, origin) {
    this.disconnect();
    const generation = this.generation;
    const open = () => {
      if (generation !== this.generation) return;
      this.onState(this.attempt ? 'RECONNECTING' : 'CONNECTING');
      const encoded = Buffer.from(token, 'utf8').toString('base64url');
      const socket = new WebSocket(url, ['fcc-agent', `auth.${encoded}`], { origin, maxPayload: 256 * 1024, handshakeTimeout: 10000 });
      this.socket = socket;
      socket.on('open', () => {
        if (generation !== this.generation) return socket.close();
        this.attempt = 0;
        this.awaitingPong = 0;
        this.onState('CONNECTED');
        this.heartbeat = setInterval(() => {
          if (this.awaitingPong && Date.now() - this.awaitingPong >= 20000) return socket.terminate();
          if (!this.awaitingPong) {
            this.awaitingPong = Date.now();
            socket.send(JSON.stringify({ type: 'HEARTBEAT_PING', timestamp: this.awaitingPong }));
          }
        }, 10000);
      });
      socket.on('message', bytes => {
        if (generation !== this.generation) return;
        try {
          const message = JSON.parse(bytes.toString());
          if (message.type === 'HEARTBEAT_PONG') this.awaitingPong = 0;
          else this.onMessage(message);
        } catch { /* Invalid envelopes do not reach the renderer. */ }
      });
      socket.on('error', () => {});
      socket.on('close', () => {
        if (generation !== this.generation) return;
        clearInterval(this.heartbeat);
        this.onState('RECONNECTING');
        this.retry = setTimeout(open, Math.min(30000, 1000 * 2 ** Math.min(this.attempt++, 5)));
      });
    };
    open();
  }

  send(message) {
    if (this.socket?.readyState === WebSocket.OPEN) this.socket.send(JSON.stringify(message));
  }

  disconnect() {
    this.generation++;
    clearTimeout(this.retry);
    clearInterval(this.heartbeat);
    this.socket?.close();
    this.socket = null;
    this.attempt = 0;
    this.onState('DISCONNECTED');
  }
}

module.exports = { Realtime };
