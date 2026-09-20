'use strict';

/** Only explicitly configured HTTPS deployments or loopback development are trusted. */
function deploymentUrl(value) {
  const url = new URL(value);
  if (url.username || url.password || url.hash || url.search) throw new Error('服务地址不得包含凭据或查询参数');
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname))) {
    throw new Error('服务地址必须使用 HTTPS（本机开发除外）');
  }
  return url;
}

/** WebSocket destination is pinned to the deployment; renderer cannot redirect tokens. */
function socketUrl(deployment, requested) {
  const expected = new URL('/ws/agent', deployment);
  expected.protocol = expected.protocol === 'https:' ? 'wss:' : 'ws:';
  if (new URL(requested).href !== expected.href) throw new Error('业务连接地址必须属于当前部署');
  return expected.href;
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
