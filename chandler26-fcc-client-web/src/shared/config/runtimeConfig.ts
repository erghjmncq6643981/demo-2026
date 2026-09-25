export interface SipRuntimeConfig {
  wsUrl: string;
  domain: string;
  password: string;
  iceServers: RTCIceServer[];
}

export interface FccRuntimeConfig {
  agentWebSocketUrl: string;
  iceServers: RTCIceServer[];
}

type BrowserLocation = Pick<Location, 'protocol' | 'host' | 'hostname' | 'href'>;

function requireWebSocketProtocol(value: string, name: string): string {
  const url = new URL(value);
  if (url.protocol !== 'ws:' && url.protocol !== 'wss:') {
    throw new Error(`${name} must use ws:// or wss://`);
  }
  return url.toString();
}

/**
 * Resolve the business WebSocket endpoint.
 * Direct connection to fcc-server (8085) without proxy.
 */
export function resolveAgentWebSocketUrl(
  configuredValue: string | undefined,
  location?: BrowserLocation,
): string {
  const configured = configuredValue?.trim();
  if (configured) {
    if (configured.startsWith('/')) {
      const browserLocation = location ?? window.location;
      const scheme = browserLocation.protocol === 'https:' ? 'wss:' : 'ws:';
      return `${scheme}//${browserLocation.hostname}:8085${configured}`;
    }
    return requireWebSocketProtocol(configured, 'VITE_FCC_AGENT_WS_URL');
  }

  const browserLocation = location ?? window.location;
  const scheme = browserLocation.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = browserLocation.hostname || 'localhost';
  return `${scheme}//${host}:8085/ws/agent`;
}

function parseIceServers(rawValue: string | undefined): RTCIceServer[] {
  if (!rawValue?.trim()) return [];
  const parsed = JSON.parse(rawValue) as unknown;
  if (!Array.isArray(parsed)) {
    throw new Error('VITE_FCC_ICE_SERVERS_JSON must be a JSON array');
  }
  return parsed as RTCIceServer[];
}

/** Load and validate browser runtime configuration exposed by Vite. */
export function loadRuntimeConfig(env: ImportMetaEnv = import.meta.env): FccRuntimeConfig {
  return {
    agentWebSocketUrl: resolveAgentWebSocketUrl(env?.VITE_FCC_AGENT_WS_URL),
    iceServers: parseIceServers(env?.VITE_FCC_ICE_SERVERS_JSON),
  };
}

/** Read runtime configuration lazily so pure model tests do not require browser globals. */
export function getRuntimeConfig(): FccRuntimeConfig {
  return loadRuntimeConfig();
}
