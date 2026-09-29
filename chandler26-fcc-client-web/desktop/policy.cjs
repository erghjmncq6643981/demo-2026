'use strict';

function isLocalOrPrivateHost(hostname) {
  if (['localhost', '127.0.0.1', '[::1]'].includes(hostname)) return true;
  if (hostname === 'fcc.local' || hostname.endsWith('.local') || hostname.endsWith('.test')) return true;
  if (/^10\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(hostname)) return true;
  if (/^192\.168\.\d{1,3}\.\d{1,3}$/.test(hostname)) return true;
  const match172 = /^172\.(\d{1,3})\.\d{1,3}\.\d{1,3}$/.exec(hostname);
  if (match172) {
    const second = parseInt(match172[1], 10);
    if (second >= 16 && second <= 31) return true;
  }
  return false;
}

/** Only explicitly configured HTTPS deployments or loopback/LAN development are trusted. */
function deploymentUrl(value) {
  const url = new URL(value);
  if (url.username || url.password || url.hash || url.search) throw new Error('服务地址不得包含凭据或查询参数');
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && isLocalOrPrivateHost(url.hostname))) {
    throw new Error('服务地址必须使用 HTTPS（本机开发或局域网除外）');
  }
  return url;
}

/** WebSocket destination is pinned to the deployment; renderer cannot redirect tokens. */
function socketUrl(deployment, requested) {
  const reqUrl = new URL(requested);
  if (reqUrl.protocol !== 'ws:' && reqUrl.protocol !== 'wss:') {
    throw new Error('业务连接必须使用 WebSocket 协议');
  }
  if (!reqUrl.pathname.endsWith('/ws/agent')) {
    throw new Error('业务连接地址必须为 /ws/agent');
  }
  if (deployment) {
    const expected = new URL('/ws/agent', deployment);
    expected.protocol = expected.protocol === 'https:' ? 'wss:' : 'ws:';
    if (reqUrl.href === expected.href) return expected.href;

    if (isLocalOrPrivateHost(reqUrl.hostname) && isLocalOrPrivateHost(deployment.hostname)) {
      return reqUrl.href;
    }
    throw new Error('业务连接地址必须属于当前部署');
  }
  if (!isLocalOrPrivateHost(reqUrl.hostname)) {
    throw new Error('业务连接地址必须属于受信任的局域网或域名');
  }
  return reqUrl.href;
}

/** Derive a bounded ringing reminder from authoritative server events. */
function ringingEvent(message, now = Date.now()) {
  if (message.type !== 'SCREEN_POP' || typeof message.callId !== 'string' || !message.callId || message.callId.length > 128) return null;
  if (!Number.isFinite(message.timestamp) || message.timestamp > now + 5000) return null;
  const seconds = Number.isFinite(message.data?.ringTimeoutSeconds) ? Math.min(120, Math.max(1, message.data.ringTimeoutSeconds)) : 30;
  const expiresAt = message.timestamp + seconds * 1000;
  return expiresAt > now ? { callId: message.callId, expiresAt } : null;
}

module.exports = { deploymentUrl, socketUrl, ringingEvent };
