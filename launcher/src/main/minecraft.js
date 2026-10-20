'use strict';
// Installs Minecraft from Mojang's official endpoints (client jar, libraries,
// assets, Java runtime) plus the Fabric loader from meta.fabricmc.net, and
// builds the launch command. Nothing from Mojang is bundled with SwiftClient.

const fs = require('fs');
const os = require('os');
const path = require('path');
const { paths } = require('./config');
const { fetchJson, fetchJsonCached, downloadAll } = require('./download');

const VERSION_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const JAVA_RUNTIMES = 'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';
const FABRIC_META = 'https://meta.fabricmc.net/v2';
const RESOURCES = 'https://resources.download.minecraft.net';
const HOUR = 3600 * 1000;

// ---- version list -----------------------------------------------------------

function compareMc(a, b) {
  const pa = a.split('.').map(Number);
  const pb = b.split('.').map(Number);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] || 0) - (pb[i] || 0);
    if (d) return d;
  }
  return 0;
}

/** Release versions >= minVersion that Fabric supports, newest first. */
async function listVersions(minVersion) {
  const [manifest, fabricGames] = await Promise.all([
    fetchJsonCached(VERSION_MANIFEST, path.join(paths.cache, 'version_manifest_v2.json'), HOUR),
    fetchJsonCached(`${FABRIC_META}/versions/game`, path.join(paths.cache, 'fabric_games.json'), HOUR),
  ]);
  const fabric = new Set(fabricGames.filter((g) => g.stable).map((g) => g.version));
  return manifest.versions
    .filter((v) => v.type === 'release' && fabric.has(v.id) && /^\d+(\.\d+)*$/.test(v.id) && compareMc(v.id, minVersion) >= 0)
    .map((v) => v.id);
}

// ---- rules ------------------------------------------------------------------

function osVersionInRange(range) {
  const rel = os.release(); // e.g. 10.0.26100
  if (range.min && compareMc(rel, range.min) < 0) return false;
  if (range.max && compareMc(rel, range.max) >= 0) return false;
  return true;
}

function rulesAllow(rules, features = {}) {
  if (!rules || !rules.length) return true;
  let allowed = false;
  for (const rule of rules) {
    let match = true;
    if (rule.os) {
      if (rule.os.name && rule.os.name !== 'windows') match = false;
      if (rule.os.arch && rule.os.arch !== (process.arch === 'ia32' ? 'x86' : process.arch)) match = false;
      if (rule.os.versionRange && !osVersionInRange(rule.os.versionRange)) match = false;
    }
    if (rule.features) {
      for (const [k, v] of Object.entries(rule.features)) if (!!features[k] !== v) match = false;
    }
    if (match) allowed = rule.action === 'allow';
  }
  return allowed;
}

function mavenPath(name) {
  const [group, artifact, version, classifier] = name.split(':');
  const file = `${artifact}-${version}${classifier ? `-${classifier}` : ''}.jar`;
  return `${group.replace(/\./g, '/')}/${artifact}/${version}/${file}`;
}

function libraryKey(name) {
  const [group, artifact, , classifier] = name.split(':');
  return `${group}:${artifact}${classifier ? `:${classifier}` : ''}`;
}

// ---- Java runtime -----------------------------------------------------------

async function installJava(component, onProgress) {
  const dir = path.join(paths.runtimes, component);
  const javaw = path.join(dir, 'bin', 'javaw.exe');
  const marker = path.join(dir, '.swiftclient-complete');
  if (fs.existsSync(javaw) && fs.existsSync(marker)) return javaw;

  const all = await fetchJsonCached(JAVA_RUNTIMES, path.join(paths.cache, 'java_runtimes.json'), HOUR);
  const platform = process.arch === 'arm64' ? 'windows-arm64' : 'windows-x64';
  const entry = (all[platform] || {})[component];
  if (!entry || !entry.length) throw new Error(`Mojang has no ${component} Java runtime for ${platform}.`);
  const manifest = await fetchJson(entry[0].manifest.url);
  const tasks = [];
  for (const [rel, f] of Object.entries(manifest.files)) {
    const target = path.join(dir, rel);
    if (f.type === 'directory') fs.mkdirSync(target, { recursive: true });
    else if (f.type === 'file') tasks.push({ url: f.downloads.raw.url, file: target, sha1: f.downloads.raw.sha1, size: f.downloads.raw.size });
  }
  await downloadAll(tasks, { onProgress, label: `Java ${entry[0].version.name}` });
  fs.writeFileSync(marker, entry[0].version.name);
  return javaw;
}

// ---- install ----------------------------------------------------------------

async function resolveFabricLoader(mc) {
  const loaders = await fetchJsonCached(`${FABRIC_META}/versions/loader/${mc}`, path.join(paths.cache, `fabric_loaders_${mc}.json`), HOUR);
  const stable = loaders.find((l) => l.loader.stable) || loaders[0];
  if (!stable) throw new Error(`Fabric does not support Minecraft ${mc} yet.`);
  return stable.loader.version;
}

/**
 * Makes sure everything needed to launch `mc` with Fabric is on disk.
 * Returns the merged launch profile.
 */
async function install(mc, onProgress = () => {}) {
  onProgress(0, 1, 'Reading version info');
  const manifest = await fetchJsonCached(VERSION_MANIFEST, path.join(paths.cache, 'version_manifest_v2.json'), HOUR);
  const entry = manifest.versions.find((v) => v.id === mc);
  if (!entry) throw new Error(`Unknown Minecraft version ${mc}.`);

  const versionDir = path.join(paths.versions, mc);
  const versionFile = path.join(versionDir, `${mc}.json`);
  await downloadAll([{ url: entry.url, file: versionFile, sha1: entry.sha1 }], { label: 'Version info' });
  const vanilla = JSON.parse(fs.readFileSync(versionFile, 'utf8'));

  const loaderVersion = await resolveFabricLoader(mc);
  const fabricId = `fabric-loader-${loaderVersion}-${mc}`;
  const fabric = await fetchJsonCached(
    `${FABRIC_META}/versions/loader/${mc}/${loaderVersion}/profile/json`,
    path.join(paths.versions, fabricId, `${fabricId}.json`),
  );

  // Java first: it is the biggest single download and is shared between versions.
  const javaComponent = (vanilla.javaVersion && vanilla.javaVersion.component) || 'java-runtime-delta';
  const java = await installJava(javaComponent, onProgress);

  // Libraries: Fabric's take precedence over vanilla entries with the same coordinates.
  const libs = new Map();
  const tasks = [];
  for (const lib of fabric.libraries) {
    const rel = mavenPath(lib.name);
    const file = path.join(paths.libraries, rel);
    libs.set(libraryKey(lib.name), file);
    tasks.push({ url: `${lib.url || 'https://maven.fabricmc.net/'}${rel}`, file, sha1: lib.sha1, size: lib.size });
  }
  for (const lib of vanilla.libraries) {
    if (!rulesAllow(lib.rules)) continue;
    const art = lib.downloads && lib.downloads.artifact;
    if (!art) continue;
    const key = libraryKey(lib.name);
    if (libs.has(key)) continue;
    const file = path.join(paths.libraries, art.path);
    libs.set(key, file);
    tasks.push({ url: art.url, file, sha1: art.sha1, size: art.size });
  }

  const clientJar = path.join(versionDir, `${mc}.jar`);
  const client = vanilla.downloads.client;
  tasks.push({ url: client.url, file: clientJar, sha1: client.sha1, size: client.size });

  let logConfig = null;
  const logging = vanilla.logging && vanilla.logging.client;
  if (logging) {
    logConfig = { arg: logging.argument, file: path.join(paths.assets, 'log_configs', logging.file.id) };
    tasks.push({ url: logging.file.url, file: logConfig.file, sha1: logging.file.sha1, size: logging.file.size });
  }

  await downloadAll(tasks, { onProgress, label: 'Game files' });

  // Assets.
  const ai = vanilla.assetIndex;
  const indexFile = path.join(paths.assets, 'indexes', `${ai.id}.json`);
  await downloadAll([{ url: ai.url, file: indexFile, sha1: ai.sha1, size: ai.size }], { label: 'Asset index' });
  const index = JSON.parse(fs.readFileSync(indexFile, 'utf8'));
  const assetTasks = Object.values(index.objects).map(({ hash, size }) => ({
    url: `${RESOURCES}/${hash.slice(0, 2)}/${hash}`,
    file: path.join(paths.assets, 'objects', hash.slice(0, 2), hash),
    sha1: hash,
    size,
  }));
  await downloadAll(assetTasks, { onProgress, label: 'Assets', concurrency: 32 });

  return {
    mc,
    id: fabricId,
    loaderVersion,
    java,
    javaMajor: (vanilla.javaVersion && vanilla.javaVersion.majorVersion) || 21,
    mainClass: fabric.mainClass,
    classpath: [...libs.values(), clientJar],
    assetIndex: ai.id,
    versionType: vanilla.type,
    logConfig,
    gameArgs: [...(vanilla.arguments.game || []), ...((fabric.arguments && fabric.arguments.game) || [])],
    jvmArgs: [...(vanilla.arguments.jvm || []), ...((fabric.arguments && fabric.arguments.jvm) || [])],
    nativesDir: path.join(versionDir, 'natives'),
  };
}

// ---- launch arguments -------------------------------------------------------

/** JVM flags tuned for a client: G1 with short pauses, which beats ZGC on low core counts. */
function performanceJvmArgs(memoryMb, javaMajor) {
  const args = [
    `-Xms${Math.min(memoryMb, 2048)}m`, `-Xmx${memoryMb}m`,
    '-XX:+UnlockExperimentalVMOptions', '-XX:+UseG1GC', '-XX:+ParallelRefProcEnabled',
    '-XX:MaxGCPauseMillis=37', '-XX:+DisableExplicitGC', '-XX:+AlwaysPreTouch',
    '-XX:G1NewSizePercent=23', '-XX:G1MaxNewSizePercent=50', '-XX:G1HeapRegionSize=16M',
    '-XX:G1ReservePercent=20', '-XX:G1MixedGCCountTarget=3', '-XX:InitiatingHeapOccupancyPercent=20',
    '-XX:G1MixedGCLiveThresholdPercent=90', '-XX:SurvivorRatio=32', '-XX:MaxTenuringThreshold=1',
    '-XX:+PerfDisableSharedMem', '-XX:+UseStringDeduplication',
  ];
  if (javaMajor >= 25) args.push('-XX:+UseCompactObjectHeaders');
  return args;
}

function expandArgs(list, vars, features) {
  const out = [];
  for (const a of list) {
    if (typeof a === 'string') out.push(a);
    else if (rulesAllow(a.rules, features)) out.push(...[].concat(a.value));
  }
  return out.map((s) => s.replace(/\$\{([^}]+)\}/g, (m, k) => (k in vars ? vars[k] : m)));
}

function buildCommand(profile, { session, gameDir, settings, launcherVersion, joinServer }) {
  fs.mkdirSync(profile.nativesDir, { recursive: true });
  const features = {
    has_custom_resolution: !settings.fullscreen,
    is_quick_play_multiplayer: !!joinServer,
  };
  const vars = {
    auth_player_name: session.name,
    version_name: profile.mc,
    game_directory: gameDir,
    assets_root: paths.assets,
    assets_index_name: profile.assetIndex,
    auth_uuid: session.uuid,
    auth_access_token: session.accessToken,
    clientid: '',
    auth_xuid: session.xuid || '',
    user_type: 'msa',
    version_type: 'SwiftClient',
    natives_directory: profile.nativesDir,
    launcher_name: 'SwiftClient',
    launcher_version: launcherVersion,
    classpath: profile.classpath.join(path.delimiter),
    classpath_separator: path.delimiter,
    library_directory: paths.libraries,
    resolution_width: String(settings.width),
    resolution_height: String(settings.height),
    quickPlayMultiplayer: joinServer || '',
  };
  const jvm = [
    ...performanceJvmArgs(settings.memoryMb, profile.javaMajor),
    ...(settings.extraJvmArgs ? settings.extraJvmArgs.split(/\s+/).filter(Boolean) : []),
    ...expandArgs(profile.jvmArgs, vars, features),
  ];
  if (profile.logConfig) jvm.push(profile.logConfig.arg.replace('${path}', profile.logConfig.file));
  const game = expandArgs(profile.gameArgs, vars, features);
  if (settings.fullscreen) game.push('--fullscreen');
  return { java: profile.java, args: [...jvm, profile.mainClass, ...game] };
}

module.exports = { listVersions, install, buildCommand, compareMc, rulesAllow, mavenPath, expandArgs, performanceJvmArgs };
