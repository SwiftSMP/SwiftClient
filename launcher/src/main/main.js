'use strict';

const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { paths, loadAppConfig, loadSettings, saveSettings } = require('./config');
const { Auth } = require('./auth');
const { Capes } = require('./capes');
const minecraft = require('./minecraft');
const mods = require('./mods');
const { DiscordPresence, activityFor, parseGameOutput } = require('./discord');

const appConfig = loadAppConfig();
const auth = new Auth(appConfig.msClientId);
const capes = new Capes(appConfig.capeApi);
let win;
let game = null; // running game process

// ---- Discord Rich Presence -------------------------------------------------------

const discord = new DiscordPresence(appConfig.discordClientId);
const presence = { phase: 'launcher', server: null, since: Date.now() };

function updatePresence(patch) {
  if (patch) {
    if (patch.phase !== presence.phase || patch.server !== presence.server) presence.since = Date.now();
    Object.assign(presence, patch);
  }
  const s = loadSettings();
  if (!s.discordPresence || !appConfig.discordClientId) { discord.stop(); return; }
  discord.start();
  discord.set(activityFor(presence, { showServer: s.discordShowServer, buttonUrl: appConfig.website }));
}

if (!app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.show(); win.focus(); } });
app.setAppUserModelId('net.swiftsmp.swiftclient');

function send(channel, payload) {
  if (win && !win.isDestroyed()) win.webContents.send(channel, payload);
}

function createWindow() {
  win = new BrowserWindow({
    width: 1120, height: 700, minWidth: 940, minHeight: 600,
    frame: false, backgroundColor: '#0d1020', show: false,
    icon: path.join(__dirname, '..', 'renderer', 'img', 'logo.png'),
    webPreferences: { preload: path.join(__dirname, '..', 'preload.js'), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  win.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'));
  win.once('ready-to-show', () => win.show());
  // Open external links in the user's browser, never inside the launcher.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('will-navigate', (e) => e.preventDefault());
}

// ---- SwiftClient's own mod, shipped with the launcher ------------------------

function swiftModFor(mc) {
  const dirs = [
    path.join(process.resourcesPath || '', 'mods'),
    path.join(__dirname, '..', '..', '..', 'mod', 'dist'),
  ];
  for (const dir of dirs) {
    try {
      const index = JSON.parse(fs.readFileSync(path.join(dir, 'index.json'), 'utf8'));
      if (index[mc]) return path.join(dir, index[mc]);
    } catch { /* try next */ }
  }
  return null;
}

// ---- IPC -----------------------------------------------------------------------

const handlers = {
  'app:info': () => ({
    version: app.getVersion(),
    server: appConfig.server,
    authConfigured: !!appConfig.msClientId,
    capesEnabled: capes.enabled(),
    dataDir: paths.root,
  }),
  'win:minimize': () => win.minimize(),
  'win:maximize': () => (win.isMaximized() ? win.unmaximize() : win.maximize()),
  'win:close': () => win.close(),
  'shell:open': (_e, url) => { if (/^https:\/\//.test(url)) shell.openExternal(url); },
  'shell:openFolder': (_e, which) => {
    const settings = loadSettings();
    const mc = settings.version;
    const map = { data: paths.root, mods: mc && mods.modsDir(mc), logs: paths.logs, instance: mc && paths.instance(mc) };
    const dir = map[which];
    if (dir) { fs.mkdirSync(dir, { recursive: true }); shell.openPath(dir); }
  },

  'auth:current': () => auth.current(),
  'auth:login': () => auth.login(win),
  'auth:logout': () => auth.logout(),

  'settings:get': () => loadSettings(),
  'settings:set': (_e, s) => { const r = saveSettings(s); updatePresence(); return r; },
  'settings:system': () => ({ totalMemMb: Math.floor(require('os').totalmem() / 1048576) }),

  'versions:list': async () => {
    const list = await minecraft.listVersions(appConfig.minVersion);
    return list.map((v) => ({ id: v, swiftMod: !!swiftModFor(v) }));
  },

  'mods:curated': () => mods.CURATED,
  'mods:list': (_e, mc) => mods.listMods(mc),
  'mods:check': (_e, mc, file) => mods.checkMod(mc, file),
  'mods:import': (_e, mc, files) => files.map((f) => ({ file: path.basename(f), ...mods.importMod(mc, f) })),
  'mods:pick': async () => {
    const r = await dialog.showOpenDialog(win, { title: 'Import mods', properties: ['openFile', 'multiSelections'], filters: [{ name: 'Fabric mods', extensions: ['jar'] }] });
    return r.canceled ? [] : r.filePaths;
  },
  'mods:toggle': (_e, mc, file, enabled) => mods.setModEnabled(mc, file, enabled),
  'mods:remove': (_e, mc, file) => mods.removeMod(mc, file),

  'capes:catalog': async () => capes.catalog(auth.current() && auth.current().uuid),
  'capes:select': async (_e, id) => capes.select(await auth.session(), id),

  'game:running': () => !!game,
  'game:launch': async (_e, opts = {}) => launch(opts),
};

for (const [channel, fn] of Object.entries(handlers)) {
  ipcMain.handle(channel, async (...args) => {
    try {
      return { ok: true, value: await fn(...args) };
    } catch (e) {
      console.error(channel, e);
      return { ok: false, error: e.message || String(e) };
    }
  });
}

// ---- launch --------------------------------------------------------------------

async function launch({ join = false } = {}) {
  if (game) throw new Error('Minecraft is already running.');
  const settings = loadSettings();
  const versions = await minecraft.listVersions(appConfig.minVersion);
  const mc = settings.version && versions.includes(settings.version) ? settings.version : versions[0];
  const progress = (done, total, label) => send('launch:progress', { done, total, label });

  if (!auth.current()) throw new Error('Sign in with the Microsoft account that owns Minecraft to play.');
  const session = await auth.session();
  progress(0, 1, `Preparing Minecraft ${mc}`);
  const profile = await minecraft.install(mc, progress);

  const swiftMod = swiftModFor(mc);
  const result = await mods.syncMods(mc, settings, { swiftMod, onProgress: progress });
  if (result.unavailable.length) send('launch:notice', `Not available for ${mc} yet: ${result.unavailable.join(', ')}`);
  if (!swiftMod) send('launch:notice', `SwiftClient capes and branding are not available for ${mc} yet.`);

  const gameDir = paths.instance(mc);
  const joinServer = (join || settings.autoJoin) ? appConfig.server.address : null;
  const cmd = minecraft.buildCommand(profile, { session, gameDir, settings, launcherVersion: app.getVersion(), joinServer });
  const props = [`-Dswiftclient.server.address=${appConfig.server.address}`, `-Dswiftclient.server.name=${appConfig.server.name}`];
  if (appConfig.capeApi) props.push(`-Dswiftclient.capeApi=${appConfig.capeApi}`);
  cmd.args.splice(cmd.args.indexOf(profile.mainClass), 0, ...props);

  fs.mkdirSync(paths.logs, { recursive: true });
  const logFile = path.join(paths.logs, 'latest-launch.log');
  const log = fs.createWriteStream(logFile);
  log.write(`SwiftClient ${app.getVersion()} launching ${profile.id}\n`);
  log.write(`${cmd.java} ${cmd.args.map((a) => (a.includes(session.accessToken) ? '<token>' : a)).join(' ')}\n\n`);

  progress(1, 1, 'Starting Minecraft');
  updatePresence({ phase: 'starting', server: null });
  game = spawn(cmd.java, cmd.args, { cwd: gameDir, windowsHide: false });
  const started = Date.now();
  const forward = (d) => {
    log.write(d);
    const text = d.toString();
    send('game:log', text);
    const p = parseGameOutput(text);
    if (p) updatePresence(p);
  };
  game.stdout.on('data', forward);
  game.stderr.on('data', forward);
  send('game:state', { running: true, mc });
  if (settings.closeOnLaunch) win.hide();

  game.on('exit', (code) => {
    log.end(`\n[SwiftClient] Minecraft exited with code ${code}\n`);
    game = null;
    updatePresence({ phase: 'launcher', server: null });
    if (win && !win.isDestroyed()) win.show();
    // A crash in the first seconds is almost always a mod problem: say so.
    const crashed = code !== 0 && Date.now() - started < 120000;
    send('game:state', { running: false, code, crashed, logFile });
  });
  game.on('error', (e) => { game = null; send('game:state', { running: false, code: -1, crashed: true, error: e.message, logFile }); });
  return { mc, loader: profile.loaderVersion };
}

// ---- auto-update -----------------------------------------------------------------

function setupUpdater() {
  if (!app.isPackaged) return;
  let autoUpdater;
  try { ({ autoUpdater } = require('electron-updater')); } catch { return; }
  autoUpdater.autoDownload = true;
  autoUpdater.on('update-available', (i) => send('update:state', { state: 'downloading', version: i.version }));
  autoUpdater.on('update-downloaded', (i) => send('update:state', { state: 'ready', version: i.version }));
  autoUpdater.on('error', () => { /* offline or no releases yet: stay quiet */ });
  ipcMain.handle('update:install', () => autoUpdater.quitAndInstall());
  autoUpdater.checkForUpdates().catch(() => {});
  setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 6 * 3600 * 1000).unref();
}

app.whenReady().then(() => {
  createWindow();
  setupUpdater();
  updatePresence();
});
app.on('window-all-closed', () => { discord.stop(); app.quit(); });
