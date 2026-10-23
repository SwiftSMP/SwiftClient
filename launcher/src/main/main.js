'use strict';

const { app, BrowserWindow, ipcMain, dialog, shell, Notification } = require('electron');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { paths, loadAppConfig, loadSettings, saveSettings } = require('./config');
const { Auth } = require('./auth');
const { Capes } = require('./capes');
const minecraft = require('./minecraft');
const mods = require('./mods');
const updates = require('./updates');
const { Stats } = require('./stats');
const { pingServer } = require('./serverping');
const { DiscordPresence, activityFor, parseGameOutput } = require('./discord');

const appConfig = loadAppConfig();
const auth = new Auth(appConfig.msClientId);
const capes = new Capes(appConfig.capeApi);
const stats = new Stats(appConfig.capeApi);
let win;
let game = null; // running game process

// ---- live status ---------------------------------------------------------------

// The featured server's player count, from a normal Minecraft server ping.
let serverStatus = { at: 0, value: null };
async function serverStatusNow(maxAgeMs = 30000) {
  if (serverStatus.value && Date.now() - serverStatus.at < maxAgeMs) return serverStatus.value;
  try {
    const value = await pingServer(appConfig.server.address, { timeoutMs: 4000 });
    serverStatus = { at: Date.now(), value };
  } catch (e) {
    serverStatus = { at: Date.now(), value: { offline: true, error: e.message } };
  }
  return serverStatus.value;
}

function restartStats() {
  const settings = loadSettings();
  stats.start(app.getVersion(), {
    enabled: settings.shareStats !== false,
    onUpdate: (counts) => send('stats:state', { client: counts }),
  });
}

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
  'settings:set': (_e, s) => { const r = saveSettings(s); updatePresence(); restartStats(); return r; },
  'settings:system': () => ({
    totalMemMb: Math.floor(require('os').totalmem() / 1048576),
    cpus: require('os').cpus().length,
    platform: `${require('os').platform()} ${require('os').arch()}`,
  }),

  'stats:get': async () => ({ client: stats.last, server: await serverStatusNow() }),

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
  'mods:search': (_e, mc, query) => mods.searchModrinth(mc, query),
  'mods:install': (_e, mc, slug) => mods.installModrinth(mc, slug),

  'capes:catalog': async () => capes.catalog(auth.current() && auth.current().uuid),
  'capes:select': async (_e, id) => capes.select(await auth.session(), id),
  'capes:redeem': async (_e, code) => capes.redeem(await auth.session(), code),

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
// electron-updater silently installs a newer release on restart. The GitHub lookup below
// covers the cases it cannot (builds without updater metadata, or a failed download): those
// clients still get told a new version exists and can download the installer in one click.

let updaterBusy = false;

/** A Windows toast, so an update is noticed even when the launcher is in the background. */
function notifyUpdate(version, body) {
  try {
    if (!Notification.isSupported()) return;
    new Notification({ title: `SwiftClient ${version}`, body }).show();
  } catch { /* notifications unavailable */ }
}

async function announceManualUpdate() {
  try {
    const release = await updates.latestRelease();
    if (!updates.isNewer(release.version, app.getVersion())) return;
    if (updaterBusy) return;
    const url = await updates.installerUrl(release);
    send('update:state', { state: 'manual', version: release.version, url });
    notifyUpdate(release.version, 'A new version is out. Click the banner in SwiftClient to get it.');
  } catch { /* offline or rate limited: try again next time */ }
}

function setupUpdater() {
  if (!app.isPackaged) return;
  let autoUpdater = null;
  try { ({ autoUpdater } = require('electron-updater')); } catch { /* fall through to the manual path */ }
  ipcMain.handle('update:install', () => { if (autoUpdater) autoUpdater.quitAndInstall(); });
  if (autoUpdater) {
    autoUpdater.autoDownload = true;
    autoUpdater.autoInstallOnAppQuit = true;
    autoUpdater.on('update-available', (i) => { updaterBusy = true; send('update:state', { state: 'downloading', version: i.version }); });
    autoUpdater.on('update-downloaded', (i) => {
      updaterBusy = true;
      send('update:state', { state: 'ready', version: i.version });
      notifyUpdate(i.version, 'Downloaded. Restart SwiftClient to install it.');
    });
    autoUpdater.on('error', () => { updaterBusy = false; /* offline or no releases yet: stay quiet */ });
    autoUpdater.checkForUpdates().catch(() => {});
    // Frequent enough that a fresh release reaches players within the hour.
    setInterval(() => autoUpdater.checkForUpdates().catch(() => {}), 60 * 60 * 1000).unref();
  }
  setTimeout(announceManualUpdate, 12000);
  setInterval(announceManualUpdate, 3 * 3600 * 1000).unref();
}

app.whenReady().then(() => {
  createWindow();
  setupUpdater();
  restartStats();
  updatePresence();
});
app.on('window-all-closed', () => { discord.stop(); stats.stop(); app.quit(); });
