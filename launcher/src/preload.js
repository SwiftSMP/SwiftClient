'use strict';
const { contextBridge, ipcRenderer, webUtils } = require('electron');

async function call(channel, ...args) {
  const r = await ipcRenderer.invoke(channel, ...args);
  if (r && r.ok === false) throw new Error(r.error);
  return r && 'value' in r ? r.value : r;
}

const on = (channel) => (fn) => {
  const listener = (_e, payload) => fn(payload);
  ipcRenderer.on(channel, listener);
  return () => ipcRenderer.removeListener(channel, listener);
};

contextBridge.exposeInMainWorld('swift', {
  call,
  // Absolute path of a dropped File (Electron removed File.path).
  pathFor: (file) => webUtils.getPathForFile(file),
  onProgress: on('launch:progress'),
  onNotice: on('launch:notice'),
  onGameState: on('game:state'),
  onGameLog: on('game:log'),
  onUpdate: on('update:state'),
  installUpdate: () => ipcRenderer.invoke('update:install'),
});
