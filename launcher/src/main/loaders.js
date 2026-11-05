'use strict';
// Non-Fabric loaders.
//
// Quilt publishes a profile JSON in the same shape as Fabric's, so it drops straight into the
// existing install/merge path. Forge and NeoForge are installed with their own official
// headless installers, pointed at the launcher's data directory (which already has the
// versions/, libraries/ layout those installers expect).

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const { paths } = require('./config');
const { fetchJsonCached, downloadAll, USER_AGENT } = require('./download');

const QUILT_META = 'https://meta.quiltmc.org/v3';
const QUILT_MAVEN = 'https://maven.quiltmc.org/repository/release/';
const FORGE_METADATA = 'https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml';
const FORGE_MAVEN = 'https://maven.minecraftforge.net/';
const NEOFORGE_METADATA = 'https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml';
const NEOFORGE_MAVEN = 'https://maven.neoforged.net/releases/';
const HOUR = 3600 * 1000;
const LOADERS = ['fabric', 'quilt', 'forge', 'neoforge'];

function mavenPath(name) {
  const [group, artifact, version, classifier] = name.split(':');
  const file = `${artifact}-${version}${classifier ? `-${classifier}` : ''}.jar`;
  return `${group.replace(/\./g, '/')}/${artifact}/${version}/${file}`;
}

async function fetchTextCached(url, cacheFile, maxAgeMs = 0) {
  try {
    if (maxAgeMs > 0 && Date.now() - fs.statSync(cacheFile).mtimeMs < maxAgeMs) {
      return fs.readFileSync(cacheFile, 'utf8');
    }
  } catch { /* no cache yet */ }
  const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
  if (!res.ok) throw new Error(`GET ${url} -> HTTP ${res.status}`);
  const text = await res.text();
  fs.mkdirSync(path.dirname(cacheFile), { recursive: true });
  fs.writeFileSync(cacheFile, text);
  return text;
}

function metadataVersions(xml) {
  return [...xml.matchAll(/<version>([^<]+)<\/version>/g)].map((m) => m[1].trim());
}

/** Newest Forge build for a Minecraft version, e.g. 1.21.1 -> 52.0.16. */
function forgeVersionFor(mc, xml) {
  const builds = metadataVersions(xml).filter((v) => v.startsWith(`${mc}-`));
  if (!builds.length) throw new Error(`Forge does not support Minecraft ${mc} yet.`);
  return builds[builds.length - 1].slice(mc.length + 1);
}

/** NeoForge numbers its releases after the Minecraft version without the leading "1.". */
function neoforgeVersionFor(mc, xml) {
  const prefix = `${mc.startsWith('1.') ? mc.slice(2) : mc}.`;
  const builds = metadataVersions(xml).filter((v) => v.startsWith(prefix));
  if (!builds.length) throw new Error(`NeoForge does not support Minecraft ${mc} yet.`);
  const stable = builds.filter((v) => !/beta|alpha|rc|snapshot/i.test(v));
  return (stable.length ? stable : builds)[(stable.length ? stable : builds).length - 1];
}

async function resolveQuilt(mc) {
  const loaders = await fetchJsonCached(`${QUILT_META}/versions/loader/${mc}`, path.join(paths.cache, `quilt_loaders_${mc}.json`), HOUR);
  const stable = loaders.find((l) => l.loader.stable) || loaders[0];
  if (!stable) throw new Error(`Quilt does not support Minecraft ${mc} yet.`);
  const loaderVersion = stable.loader.version;
  const id = `quilt-loader-${loaderVersion}-${mc}`;
  const profile = await fetchJsonCached(
    `${QUILT_META}/versions/loader/${mc}/${loaderVersion}/profile/json`,
    path.join(paths.versions, id, `${id}.json`),
  );
  return { id, loaderVersion, mainClass: profile.mainClass, arguments: profile.arguments, libraries: profile.libraries, maven: QUILT_MAVEN };
}

function runInstaller(java, installerJar, onProgress) {
  return new Promise((resolve, reject) => {
    // Forge's installer refuses to work without the official launcher's profile file.
    const profileFile = path.join(paths.root, 'launcher_profiles.json');
    if (!fs.existsSync(profileFile)) {
      fs.mkdirSync(paths.root, { recursive: true });
      fs.writeFileSync(profileFile, JSON.stringify({ profiles: {}, clientToken: '', authenticationDatabase: {} }));
    }
    const child = spawn(java, ['-jar', installerJar, '--installClient', paths.root], { stdio: ['ignore', 'pipe', 'pipe'] });
    let log = '';
    child.stdout.on('data', (d) => { log += d; onProgress(0, 1, 'Installing loader'); });
    child.stderr.on('data', (d) => { log += d; });
    child.on('error', reject);
    child.on('exit', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`The loader installer exited with code ${code}:\n${log.slice(-1500)}`));
    });
  });
}

/** Turns an installer-produced version JSON into the Fabric-shaped profile the merger expects. */
function normalizeInstallerProfile(profile, mavenBase) {
  return {
    id: profile.id,
    mainClass: profile.mainClass,
    arguments: profile.arguments || { game: [], jvm: [] },
    libraries: profile.libraries.map((lib) => {
      const rel = mavenPath(lib.name);
      const art = lib.downloads && lib.downloads.artifact;
      return {
        name: lib.name,
        url: (art && art.url) || `${lib.url || mavenBase}${rel}`,
        sha1: (art && art.sha1) || lib.sha1,
        size: (art && art.size) || lib.size,
      };
    }),
    maven: mavenBase,
  };
}

async function resolveInstallerLoader(mc, loader, java, onProgress) {
  const isForge = loader === 'forge';
  const xml = await fetchTextCached(
    isForge ? FORGE_METADATA : NEOFORGE_METADATA,
    path.join(paths.cache, isForge ? 'forge_metadata.xml' : 'neoforge_metadata.xml'),
    HOUR,
  );
  const version = isForge ? forgeVersionFor(mc, xml) : neoforgeVersionFor(mc, xml);
  const mavenBase = isForge ? FORGE_MAVEN : NEOFORGE_MAVEN;
  const artifact = isForge ? `forge-${mc}-${version}-installer.jar` : `neoforge-${version}-installer.jar`;
  const installerUrl = isForge
    ? `${mavenBase}net/minecraftforge/forge/${mc}-${version}/${artifact}`
    : `${mavenBase}net/neoforged/neoforge/${version}/${artifact}`;
  const versionJson = isForge
    ? path.join(paths.versions, `${mc}-forge-${version}`, `${mc}-forge-${version}.json`)
    : path.join(paths.versions, `neoforge-${version}`, `neoforge-${version}.json`);

  if (!fs.existsSync(versionJson)) {
    const installerJar = path.join(paths.cache, 'installers', artifact);
    await downloadAll([{ url: installerUrl, file: installerJar }], { onProgress, label: `${loader} installer` });
    onProgress(0, 1, `Installing ${loader}`);
    await runInstaller(java, installerJar, onProgress);
  }
  if (!fs.existsSync(versionJson)) {
    throw new Error(`The ${loader} installer finished but ${path.basename(versionJson)} is missing.`);
  }
  const profile = normalizeInstallerProfile(JSON.parse(fs.readFileSync(versionJson, 'utf8')), mavenBase);
  profile.loaderVersion = version;
  return profile;
}

/** Resolves any supported loader to a Fabric-shaped profile for minecraft.install(). */
async function resolveLoader(mc, loader, java, onProgress) {
  if (loader === 'quilt') return resolveQuilt(mc);
  if (loader === 'forge' || loader === 'neoforge') return resolveInstallerLoader(mc, loader, java, onProgress);
  throw new Error(`Unknown loader ${loader}. Expected one of ${LOADERS.join(', ')}.`);
}

module.exports = { LOADERS, resolveLoader, forgeVersionFor, neoforgeVersionFor, normalizeInstallerProfile, mavenPath };
