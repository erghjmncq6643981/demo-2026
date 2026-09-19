import { ref } from 'vue';
import type { WsMessage } from '../types/telephony';

type MessageHandler = (msg: WsMessage) => void;

class WebSocketService {
  private ws: WebSocket | null = null;
  private workNo: string = '901001';
  private handlers: Set<MessageHandler> = new Set();
  private reconnectTimer: number | null = null;
  private heartbeatTimer: number | null = null;
  
  // 使用 Vue 的 ref 保证响应式联动
  public isConnected = ref(false);
  public lastPingTime: number = 0;
  public rttMs = ref(5);
  public connectionUrl = ref('');

  public connect(workNo: string) {
    this.workNo = workNo;
    const host = window.location.hostname || '127.0.0.1';
    this.connectionUrl.value = `ws://${host}:8085/ws/agent?workNo=${encodeURIComponent(this.workNo)}`;

    try {
      if (this.ws) {
        this.ws.close();
      }

      this.ws = new WebSocket(this.connectionUrl.value);

      this.ws.onopen = () => {
        console.log('🔌 [WebSocket] 信道建立成功:', this.connectionUrl.value);
        this.isConnected.value = true;
        this.startHeartbeat();
      };

      this.ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data) as WsMessage;
          // 心跳响应只用于计算 RTT，不再向下游业务分发
          if (msg.type === 'HEARTBEAT_PONG') {
            this.rttMs.value = Math.max(1, Date.now() - this.lastPingTime);
            return;
          }
          this.handlers.forEach((fn) => fn(msg));
        } catch (e) {
          console.warn('WS message parse error:', e);
        }
      };

      this.ws.onclose = () => {
        this.isConnected.value = false;
        this.stopHeartbeat();
        this.scheduleReconnect();
      };

      this.ws.onerror = (err) => {
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
        this.lastPingTime = Date.now();
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
  }

  private scheduleReconnect() {
    if (this.reconnectTimer !== null) return;
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      console.log('🔄 [WebSocket] 尝试自动断线重连...');
      this.connect(this.workNo);
    }, 4000);
  }

  public subscribe(handler: MessageHandler) {
    this.handlers.add(handler);
    return () => this.handlers.delete(handler);
  }

  public send(msg: Partial<WsMessage>) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  public disconnect() {
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
    console.log('🔌 [WebSocket] 信道已安全注销关闭');
  }
}

export const wsService = new WebSocketService();
