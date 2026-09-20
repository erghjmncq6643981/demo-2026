export {};
declare global {
  interface Window {
    fccDesktop?: {
      connect(config: { url: string; token: string }): Promise<void>;
      disconnect(): Promise<void>;
      send(message: unknown): Promise<void>;
      onState(callback: (state: 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING') => void): () => void;
      onMessage(callback: (message: import('../../types/telephony').WsMessage) => void): () => void;
    };
  }
}
