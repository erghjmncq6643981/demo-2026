'use strict';
const { contextBridge, ipcRenderer } = require('electron');
const subscribe = (channel, callback) => {
  const listener = (_event, data) => callback(data);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
};
contextBridge.exposeInMainWorld('fccDesktop', Object.freeze({
  connect: config => ipcRenderer.invoke('fcc:connect', config),
  disconnect: () => ipcRenderer.invoke('fcc:disconnect'),
  send: message => ipcRenderer.invoke('fcc:send', message),
  onState: callback => subscribe('fcc:state', callback),
  onMessage: callback => subscribe('fcc:message', callback),
}));
