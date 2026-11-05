'use strict';
// Paths, build-time config (swiftclient.config.json) and user settings.

const fs = require('fs');
const path = require('path');
const os = require('os');

/**
 * Microsoft Store (MSIX) build: Windows silently redirects AppData writes of packaged apps
 * into the package's own folder. Java then sees different paths for the jars than the
 * launcher put on the classpath, and Fabric loads its classes twice and crashes. So the
 * Store build keeps its data in the package's real LocalState folder, which is never
 * redirected. The folder is named after the package family name, Name_PublisherId.
 */
function storeRoot() {
  if (!process.windowsStore) return null;
  const family = packageFamilyName();
  const local = process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local');
  return family ? path.join(local, 'Packages', family, 'LocalState', '.swiftclient') : null;
}

/** Package family name from the app's AppxManifest.xml (works for Store and sideloaded installs). */
function packageFamilyName() {
  for (const dir of [path.dirname(process.execPath), path.join(path.dirname(process.execPath), '..')]) {
    let xml;
    try { xml = fs.readFileSync(path.join(dir, 'AppxManifest.xml'), 'utf8'); } catch { continue; }
    const identity = /<Identity\b[^>]*>/.exec(xml);
    if (!identity) return null;
    const attr = (n) => (new RegExp(`\\b${n}\\s*=\\s*(?:"([^"]*)"|'([^']*)')`).exec(identity[0]) || []).slice(1).find((v) => v != null);
    const name = attr('Name');
    const publisher = attr('Publisher');
    return name && publisher ? `${name}_${publisherId(publisher)}` : null;
  }
  return null;
}

/** Windows' publisher ID: first 8 bytes of SHA-256(UTF-16LE publisher), base32 (Crockford alphabet), 13 chars. */
function publisherId(publisher) {
  const hash = require('crypto').createHash('sha256').update(Buffer.from(publisher, 'utf16le')).digest().subarray(0, 8);
  const alphabet = '0123456789abcdefghjkmnpqrstvwxyz';
  let bits = '';
  for (const byte of hash) bits += byte.toString(2).padStart(8, '0');
  bits += '0';
  let out = '';
  for (let i = 0; i < 65; i += 5) out += alphabet[parseInt(bits.slice(i, i + 5), 2)];
  return out;
}

// SWIFTCLIENT_HOME overrides the data folder (portable installs, testing).
const ROOT = process.env.SWIFTCLIENT_HOME
  || storeRoot()
  || path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), '.swiftclient');

const paths = {
  root: ROOT,
  versions: path.join(ROOT, 'versions'),
  libraries: path.join(ROOT, 'libraries'),
  assets: path.join(ROOT, 'assets'),
  runtimes: path.join(ROOT, 'runtime'),
  instances: path.join(ROOT, 'instances'),
  cache: path.join(ROOT, 'cache'),
  logs: path.join(ROOT, 'logs'),
  settings: path.join(ROOT, 'settings.json'),
  account: path.join(ROOT, 'account.bin'),
  instance: (mc) => path.join(ROOT, 'instances', mc),
  // Non-Fabric loaders get their own instance dirs so mods never mix across loaders.
  instanceFor: (mc, loader) => path.join(ROOT, 'instances', !loader || loader === 'fabric' ? mc : `${mc}-${loader}`),
};

const DEFAULT_APP_CONFIG = {
  // Azure app registration (public client, "Personal Microsoft accounts" audience).
  // Must be approved by Mojang for the Minecraft services API. See README.
  msClientId: '',
  // Base URL of the SwiftClient cape server (cape-server/). Empty disables shared capes.
  capeApi: 'https://capes.swiftsmp.com',
  // Featured server shown on the play screen.
  server: { name: 'SwiftSMP', address: 'swiftsmp.net' },
  // Discord application id for Rich Presence (discord.com/developers). Empty disables it.
  discordClientId: '',
  // Link for the presence button and the website.
  website: 'https://swiftsmp.com',
  // Oldest version shown in the version picker.
  minVersion: '1.21',
};

function loadAppConfig() {
  // Lookup order: next to the packaged app (resources/), then the source tree.
  const candidates = [
    process.resourcesPath && path.join(process.resourcesPath, 'swiftclient.config.json'),
    path.join(__dirname, '..', '..', 'swiftclient.config.json'),
  ].filter(Boolean);
  for (const file of candidates) {
    try {
      return { ...DEFAULT_APP_CONFIG, ...JSON.parse(fs.readFileSync(file, 'utf8')) };
    } catch { /* try next */ }
  }
  return { ...DEFAULT_APP_CONFIG };
}

const DEFAULT_SETTINGS = {
  version: null,          // selected Minecraft version; null = newest supported
  loader: 'fabric',       // fabric, quilt, forge or neoforge
  memoryMb: defaultMemory(),
  width: 1280,
  height: 720,
  fullscreen: false,
  performanceMods: true,  // install the curated performance mod set
  optionalMods: {},       // curated mod id -> enabled (overrides its default)
  closeOnLaunch: false,
  autoJoin: false,        // join the featured server straight away
  extraJvmArgs: '',
  discordPresence: true,  // show SwiftClient activity on Discord
  discordShowServer: true, // include the server address
  shareStats: true,       // anonymous install/online counters on the cape server
};

function defaultMemory() {
  const totalMb = Math.floor(os.totalmem() / 1024 / 1024);
  // A quarter of RAM, between 2 and 6 GB: more than 6 GB mostly lengthens GC pauses.
  return Math.max(2048, Math.min(6144, Math.floor(totalMb / 4 / 512) * 512));
}

function loadSettings() {
  try {
    return { ...DEFAULT_SETTINGS, ...JSON.parse(fs.readFileSync(paths.settings, 'utf8')) };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

function saveSettings(settings) {
  fs.mkdirSync(paths.root, { recursive: true });
  const merged = { ...loadSettings(), ...settings };
  fs.writeFileSync(paths.settings, JSON.stringify(merged, null, 2));
  return merged;
}

module.exports = { paths, loadAppConfig, loadSettings, saveSettings, DEFAULT_SETTINGS };
