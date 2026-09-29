'use strict';
const { app, BrowserWindow, Menu, Tray, Notification, ipcMain, dialog, nativeImage } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const { deploymentUrl, socketUrl, ringingEvent } = require('./policy.cjs');
const { Realtime } = require('./realtime.cjs');

app.setAppUserModelId('com.chandler.fcc.agent');
let window, tray, quitting = false, deployment;
const reminders = new Map();
const seen = new Set();
const terminal = new Set();
const emit = (channel, data) => { if (window && !window.isDestroyed()) window.webContents.send(channel, data); };
const reveal = () => { if (!window) return; if (window.isMinimized()) window.restore(); window.show(); window.focus(); };
const remember = (set, id) => { set.add(id); if (set.size > 2048) set.delete(set.values().next().value); };
const dismiss = id => {
  const reminder = reminders.get(id);
  if (reminder) { clearTimeout(reminder.timer); reminder.notification?.close(); reminders.delete(id); }
  if (!reminders.size) window?.flashFrame(false);
};
const reset = () => { for (const id of reminders.keys()) dismiss(id); seen.clear(); terminal.clear(); };

const realtime = new Realtime(state => emit('fcc:state', state), message => {
  if (['CALL_ANSWERED', 'CALL_HANGUP'].includes(message.type) && typeof message.callId === 'string') {
    remember(terminal, message.callId); dismiss(message.callId);
  }
  const ring = ringingEvent(message);
  if (ring) realtime.send({ type: 'SCREEN_POP_RECEIPT', callId: ring.callId, state: 'RECEIVED' });
  if (ring && !seen.has(ring.callId) && !terminal.has(ring.callId)) {
    remember(seen, ring.callId);
    window?.flashFrame(true);
    // Lock-screen notification never exposes customer names or phone numbers.
    const notification = Notification.isSupported() ? new Notification({ title: 'FCC 来电', body: '有新的来电，请打开坐席工作台处理。', timeoutType: 'never' }) : null;
    const record = { notification, timer: setTimeout(() => dismiss(ring.callId), ring.expiresAt - Date.now()) };
    reminders.set(ring.callId, record);
    notification?.on('show', () => realtime.send({ type: 'SCREEN_POP_RECEIPT', callId: ring.callId, state: 'SHOWN' }));
    notification?.on('click', () => {
      realtime.send({ type: 'SCREEN_POP_RECEIPT', callId: ring.callId, state: 'ACTIVATED' });
      reveal();
    });
    if (!notification) realtime.send({ type: 'SCREEN_POP_RECEIPT', callId: ring.callId, state: 'UNSUPPORTED' });
    notification?.show();
  }
  emit('fcc:message', message);
});

function trusted(event) {
  if (event.sender !== window?.webContents || event.senderFrame !== window.webContents.mainFrame) throw new Error('拒绝非工作台调用');
  if (deployment && new URL(event.senderFrame.url).origin !== deployment.origin) throw new Error('拒绝非工作台调用');
}

if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', reveal);
  app.on('before-quit', () => { quitting = true; realtime.disconnect(); reset(); });
  app.whenReady().then(() => {
    const localHtmlPath = path.join(__dirname, '../dist/index.html');
    const hasLocalBundle = fs.existsSync(localHtmlPath);
    const configPath = path.join(app.getPath('userData'), 'deployment.json');
    let saved;
    try { saved = JSON.parse(fs.readFileSync(configPath, 'utf8')).url; } catch {}

    const remoteTarget = process.argv.find(arg => arg.startsWith('--server='))?.slice(9) || process.env.FCC_DESKTOP_URL || saved;
    if (remoteTarget) {
      try { deployment = deploymentUrl(remoteTarget); } catch {}
    }

    if (!deployment && !hasLocalBundle) {
      window = new BrowserWindow({ width: 580, height: 420, webPreferences: { preload: path.join(__dirname, 'setup-preload.cjs'), contextIsolation: true, sandbox: true, nodeIntegration: false } });
      window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
      window.webContents.on('will-navigate', event => event.preventDefault());
      ipcMain.handle('fcc:configure', (event, value) => {
        if (event.sender !== window.webContents || event.senderFrame !== window.webContents.mainFrame) throw new Error('非法配置请求');
        const url = deploymentUrl(value).href;
        fs.mkdirSync(app.getPath('userData'), { recursive: true });
        fs.writeFileSync(configPath, JSON.stringify({ url }), { mode: 0o600 });
        app.relaunch({ args: process.argv.slice(1).filter(arg => !arg.startsWith('--server=')).concat(`--server=${url}`) });
        app.quit();
      });
      window.loadFile(path.join(__dirname, 'setup.html'));
      return;
    }

    window = new BrowserWindow({ width: 1380, height: 900, minWidth: 1000, minHeight: 680, webPreferences: { preload: path.join(__dirname, 'preload.cjs'), contextIsolation: true, sandbox: true, nodeIntegration: false, backgroundThrottling: false } });
    window.on('close', () => {
      quitting = true;
      realtime.disconnect();
      reset();
      app.quit();
    });
    window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
    window.webContents.on('will-navigate', (event, url) => {
      if (deployment && new URL(url).origin !== deployment.origin) event.preventDefault();
    });
    window.webContents.on('will-redirect', (event, url) => {
      if (deployment && new URL(url).origin !== deployment.origin) event.preventDefault();
    });
    window.webContents.session.setPermissionRequestHandler((contents, permission, callback) => {
      const url = contents.getURL();
      const isTrustedMediaRequest = (url.startsWith('file://') || (deployment && url.startsWith(deployment.origin))) && permission === 'media';
      callback(isTrustedMediaRequest);
    });
    window.webContents.session.setPermissionCheckHandler((contents, permission) => {
      const url = contents.getURL();
      return (url.startsWith('file://') || (deployment && url.startsWith(deployment.origin))) && permission === 'media';
    });
    const icon = nativeImage.createFromDataURL('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==');
    tray = new Tray(icon);
    tray.setToolTip('FCC 坐席工作台');
    const startArgs = deployment ? [`--server=${deployment.href}`] : [];
    tray.setContextMenu(Menu.buildFromTemplate([
      { label: '打开工作台', click: reveal },
      { label: '开机启动', type: 'checkbox', checked: app.getLoginItemSettings().openAtLogin, click: item => app.setLoginItemSettings({ openAtLogin: item.checked, args: startArgs }) },
      { label: '退出', click: () => app.quit() }
    ]));
    tray.on('double-click', reveal);
    ipcMain.handle('fcc:connect', (event, config) => {
      trusted(event);
      if (typeof config?.token !== 'string' || !config.token || config.token.length > 8192) throw new Error('登录身份无效');
      reset();
      const origin = deployment ? deployment.origin : 'http://fcc.local';
      realtime.connect(socketUrl(deployment, config.url), config.token, origin);
    });
    ipcMain.handle('fcc:disconnect', event => { trusted(event); realtime.disconnect(); reset(); });
    ipcMain.handle('fcc:send', (event, message) => {
      trusted(event);
      if (!['HEARTBEAT_PING'].includes(message?.type)) throw new Error('不支持的桌面消息');
      realtime.send({ type: message.type, timestamp: Date.now() });
    });
    if (deployment) {
      window.loadURL(deployment.href);
    } else {
      window.loadFile(localHtmlPath);
    }
  });
}
