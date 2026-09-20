import { ref } from 'vue';
import type { WsMessage } from '../types/telephony';
import { getRuntimeConfig } from '../shared/config/runtimeConfig';

type MessageHandler = (msg: WsMessage) => void;

class WebSocketService {
  private ws: WebSocket | null = null;
  private workNo: string = '';
  private handlers: Set<MessageHandler> = new Set();
  private reconnectTimer: number | null = null;
  private heartbeatTimer: number | null = null;
  private awaitingPongSince: number | null = null;
  private reconnectAttempt = 0;
  private shouldReconnect = false;
  private desktopCleanup: Array<() => void> = [];
  
  // 使用 Vue 的 ref 保证响应式联动
  public isConnected = ref(false);
  public lastPingTime: number = 0;
  public rttMs = ref(0);
  public connectionUrl = ref('');
  public connectionState = ref<'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING'>('DISCONNECTED');

  public connect(workNo: string) {
    this.workNo = workNo;
    this.shouldReconnect = true;
    const url = new URL(getRuntimeConfig().agentWebSocketUrl);
    this.connectionUrl.value = url.toString();
    this.connectionState.value = this.reconnectAttempt > 0 ? 'RECONNECTING' : 'CONNECTING';

    if (window.fccDesktop) {
      const desktop = window.fccDesktop;
      this.desktopCleanup.forEach(cleanup => cleanup());
      this.desktopCleanup = [desktop.onState(state => {
        this.connectionState.value = state;
        this.isConnected.value = state === 'CONNECTED';
      }), desktop.onMessage(message => this.handlers.forEach(handler => handler(message)))];
      const token = localStorage.getItem('fcc_agent_satoken');
      if (!token) { this.disconnect(); return; }
      void desktop.connect({ url: this.connectionUrl.value, token }).catch(() => this.disconnect());
      return;
    }

    try {
      if (this.ws) {
        this.ws.onclose = null;
        this.ws.close();
      }

      const token = localStorage.getItem('fcc_agent_satoken');
      if (!token) { this.disconnect(); return; }
      const encoded = btoa(token).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
      const socket = new WebSocket(this.connectionUrl.value, ['fcc-agent', `auth.${encoded}`]);
      this.ws = socket;

      socket.onopen = () => {
        if (this.ws !== socket) return;
        console.log('🔌 [WebSocket] 信道建立成功:', this.connectionUrl.value);
        this.isConnected.value = true;
        this.connectionState.value = 'CONNECTED';
        this.reconnectAttempt = 0;
        this.startHeartbeat();
      };

      socket.onmessage = (event) => {
        if (this.ws !== socket) return;
        try {
          const msg = JSON.parse(event.data) as WsMessage;
          // 心跳响应只用于计算 RTT，不再向下游业务分发
          if (msg.type === 'HEARTBEAT_PONG') {
            this.rttMs.value = Math.max(1, Date.now() - this.lastPingTime);
            this.awaitingPongSince = null;
            return;
          }
          this.handlers.forEach((fn) => fn(msg));
        } catch (e) {
          console.warn('WS message parse error:', e);
        }
      };

      socket.onclose = () => {
        if (this.ws !== socket) return;
        this.isConnected.value = false;
        this.stopHeartbeat();
        this.ws = null;
        if (this.shouldReconnect) {
          this.connectionState.value = 'RECONNECTING';
          this.scheduleReconnect();
        } else {
          this.connectionState.value = 'DISCONNECTED';
        }
      };

      socket.onerror = (err) => {
        if (this.ws !== socket) return;
        console.error('🔌 [WebSocket] 通信异常:', err);
        this.isConnected.value = false;
      };
    } catch (e) {
      console.warn('WebSocket connect failed, scheduling reconnect:', e);
      this.scheduleReconnect();
    }
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = window.setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        if (this.awaitingPongSince !== null && Date.now() - this.awaitingPongSince > 20000) {
          console.warn('[WebSocket] 心跳响应超时，主动关闭连接以触发重连');
          this.ws.close(4000, 'heartbeat timeout');
          return;
        }
        this.lastPingTime = Date.now();
        this.awaitingPongSince = this.lastPingTime;
        this.ws.send(JSON.stringify({
          type: 'HEARTBEAT_PING',
          workNo: this.workNo,
          timestamp: this.lastPingTime,
        }));
      }
    }, 10000);
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer !== null) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    this.awaitingPongSince = null;
  }

  private scheduleReconnect() {
    if (this.reconnectTimer !== null) return;
    const delayMs = Math.min(30000, 1000 * (2 ** this.reconnectAttempt));
    this.reconnectAttempt += 1;
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      if (!this.shouldReconnect) return;
      console.log(`🔄 [WebSocket] 执行第 ${this.reconnectAttempt} 次自动重连`);
      this.connect(this.workNo);
    }, delayMs);
  }

  public subscribe(handler: MessageHandler) {
    this.handlers.add(handler);
    return () => this.handlers.delete(handler);
  }

  public send(msg: Partial<WsMessage>) {
    if (window.fccDesktop) { void window.fccDesktop.send(msg).catch(() => {}); return; }
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  public disconnect() {
    this.desktopCleanup.forEach(cleanup => cleanup());
    this.desktopCleanup = [];
    if (window.fccDesktop) void window.fccDesktop.disconnect().catch(() => {});
    this.shouldReconnect = false;
    this.stopHeartbeat();
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.isConnected.value = false;
    this.connectionState.value = 'DISCONNECTED';
    this.reconnectAttempt = 0;
    console.log('🔌 [WebSocket] 信道已安全注销关闭');
  }
}

export const wsService = new WebSocketService();
