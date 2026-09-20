const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('setup', { save: url => ipcRenderer.invoke('fcc:configure', url) });
