'use strict';
// Curated mod set (downloaded from Modrinth at install time, never bundled)
// and user mod import with compatibility checks.

const fs = require('fs');
const path = require('path');
const AdmZip = require('adm-zip');
const semver = require('semver');
const { paths } = require('./config');
const { fetchJsonCached, downloadAll } = require('./download');

const MODRINTH = 'https://api.modrinth.com/v2';

// group: 'core' is always installed; 'performance' follows the performance toggle;
// 'optional' follows settings.optionalMods[slug] ?? on.
const CURATED = [
  { slug: 'fabric-api', name: 'Fabric API', group: 'core' },
  { slug: 'sodium', name: 'Sodium', group: 'performance', desc: 'Modern renderer, large FPS gains' },
  { slug: 'lithium', name: 'Lithium', group: 'performance', desc: 'Game logic and physics optimizations' },
  { slug: 'ferrite-core', name: 'FerriteCore', group: 'performance', desc: 'Lower memory use' },
  { slug: 'immediatelyfast', name: 'ImmediatelyFast', group: 'performance', desc: 'Faster HUD, text and entity rendering' },
  { slug: 'entityculling', name: 'EntityCulling', group: 'performance', desc: 'Skips rendering hidden entities' },
  { slug: 'moreculling', name: 'More Culling', group: 'performance', desc: 'Culls more hidden block faces' },
  { slug: 'badoptimizations', name: 'BadOptimizations', group: 'performance', desc: 'Caches lightmap, sky and more' },
  { slug: 'modernfix', name: 'ModernFix', group: 'performance', desc: 'Faster startup, less memory' },
  { slug: 'krypton', name: 'Krypton', group: 'performance', desc: 'Optimized networking stack' },
  { slug: 'dynamic-fps', name: 'Dynamic FPS', group: 'performance', desc: 'Throttles FPS when the game is in the background' },
  { slug: 'sodium-extra', name: 'Sodium Extra', group: 'optional', default: true, desc: 'More video settings' },
  { slug: 'reeses-sodium-options', name: "Reese's Sodium Options", group: 'optional', default: true, desc: 'Better video settings screen' },
  { slug: 'modmenu', name: 'Mod Menu', group: 'optional', default: true, desc: 'Mods button on the title screen' },
  { slug: 'zoomify', name: 'Zoomify', group: 'optional', default: true, desc: 'Zoom key (C)' },
  { slug: 'iris', name: 'Iris Shaders', group: 'optional', default: false, desc: 'Shader pack support' },
];

// Mods that break the curated set; importing them is refused with a reason.
const INCOMPATIBLE = {
  optifine: 'OptiFine conflicts with Sodium. SwiftClient already includes equivalent performance mods.',
  optifabric: 'OptiFine conflicts with Sodium. SwiftClient already includes equivalent performance mods.',
  canvas: 'Canvas replaces the renderer and conflicts with Sodium.',
  rubidium: 'Rubidium is a Forge port of Sodium; SwiftClient already includes Sodium.',
  embeddium: 'Embeddium is a Forge port of Sodium; SwiftClient already includes Sodium.',
};

// ---- instance state ---------------------------------------------------------

function modsDir(mc) { return path.join(paths.instance(mc), 'mods'); }
function stateFile(mc) { return path.join(paths.instance(mc), 'swiftclient-mods.json'); }

function readState(mc) {
  try { return JSON.parse(fs.readFileSync(stateFile(mc), 'utf8')); } catch { return { managed: {} }; }
}

function writeState(mc, state) {
  fs.mkdirSync(paths.instance(mc), { recursive: true });
  fs.writeFileSync(stateFile(mc), JSON.stringify(state, null, 2));
}

// ---- fabric.mod.json --------------------------------------------------------

function readModMeta(jarPath) {
  let zip;
  try { zip = new AdmZip(jarPath); } catch { return { kind: 'invalid' }; }
  const entry = (name) => zip.getEntry(name);
  const fmj = entry('fabric.mod.json');
  if (fmj) {
    try {
      // Some mods ship fabric.mod.json with control characters in strings; strip them.
      const raw = fmj.getData().toString('utf8').replace(/^﻿/, '').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '');
      const m = JSON.parse(raw);
      return {
        kind: 'fabric', id: m.id, name: m.name || m.id, version: String(m.version || ''),
        provides: m.provides || [], depends: m.depends || {}, breaks: m.breaks || {},
        environment: m.environment || '*',
      };
    } catch (e) {
      return { kind: 'invalid', error: `fabric.mod.json is not valid JSON (${e.message})` };
    }
  }
  if (entry('quilt.mod.json')) return { kind: 'quilt' };
  if (entry('META-INF/neoforge.mods.toml')) return { kind: 'neoforge' };
  if (entry('META-INF/mods.toml') || entry('mcmod.info')) return { kind: 'forge' };
  if (entry('optifine/Config.class') || entry('net/optifine/Config.class')) return { kind: 'fabric', id: 'optifine', name: 'OptiFine' };
  return { kind: 'unknown' };
}

/** Fabric version predicate check. Returns true, false, or null when it cannot tell. */
function versionMatches(version, predicate) {
  if (predicate == null) return true;
  if (Array.isArray(predicate)) {
    const results = predicate.map((p) => versionMatches(version, p));
    if (results.some((r) => r === true)) return true;
    return results.some((r) => r === null) ? null : false;
  }
  const v = semver.coerce(version);
  if (!v) return null;
  let range = String(predicate).trim();
  if (range === '*' || range === '') return true;
  // Fabric allows "1.21.x", ">=1.21-" (any prerelease) and exact "1.21".
  range = range.replace(/-(?=\s|$)/g, '').replace(/\.x\b/g, '.x');
  range = range.split(/\s+/).map((part) => {
    const m = part.match(/^([<>=~^]*)(\d+(?:\.\d+)*)(.*)$/);
    if (!m) return part;
    const nums = m[2].split('.');
    // Exact "1.21" means 1.21.0 for Fabric; pad to three parts unless it uses ~ or ^.
    while (nums.length < 3 && !m[1].match(/[~^]/) && !m[3].startsWith('.x')) nums.push('0');
    return `${m[1]}${nums.join('.')}${m[3].replace(/^-.*/, '')}`;
  }).join(' ');
  try {
    return semver.satisfies(v, range, { loose: true, includePrerelease: true });
  } catch { return null; }
}

// ---- curated set ------------------------------------------------------------

function wantedCurated(settings) {
  return CURATED.filter((m) => {
    if (m.group === 'core') return true;
    if (m.group === 'performance') return settings.performanceMods !== false;
    const o = settings.optionalMods || {};
    return o[m.slug] ?? m.default;
  });
}

async function resolveModrinth(slug, mc) {
  const url = `${MODRINTH}/project/${slug}/version?` + new URLSearchParams({
    loaders: JSON.stringify(['fabric']), game_versions: JSON.stringify([mc]),
  });
  const versions = await fetchJsonCached(url, path.join(paths.cache, 'modrinth', `${slug}_${mc}.json`), 6 * 3600 * 1000);
  const order = { release: 0, beta: 1, alpha: 2 };
  const best = versions.slice().sort((a, b) =>
    (order[a.version_type] - order[b.version_type]) || (Date.parse(b.date_published) - Date.parse(a.date_published)))[0];
  if (!best) return null;
  const file = best.files.find((f) => f.primary) || best.files[0];
  return {
    slug, projectId: best.project_id, versionId: best.id, version: best.version_number, filename: file.filename,
    url: file.url, sha512: file.hashes.sha512, size: file.size,
    requires: best.dependencies.filter((d) => d.dependency_type === 'required').map((d) => d.project_id),
  };
}

/**
 * Installs/updates curated mods (and SwiftClient's own mod) for `mc`, removing
 * curated files that are no longer wanted. User mods are left alone.
 */
async function syncMods(mc, settings, { swiftMod, onProgress = () => {} } = {}) {
  const dir = modsDir(mc);
  fs.mkdirSync(dir, { recursive: true });
  const state = readState(mc);
  const wanted = wantedCurated(settings);

  onProgress(0, wanted.length, 'Resolving mods');
  const resolved = await Promise.all(wanted.map((m) => resolveModrinth(m.slug, mc).catch(() => null)));
  const plan = new Map(); // projectId -> resolved version
  const unavailable = [];
  wanted.forEach((m, i) => (resolved[i] ? plan.set(resolved[i].projectId, resolved[i]) : unavailable.push(m.name)));

  // Pull in required libraries (e.g. Cloth Config, YACL), a few levels deep.
  for (let depth = 0; depth < 3; depth++) {
    const missing = [...new Set([...plan.values()].flatMap((p) => p.requires).filter((id) => !plan.has(id)))];
    if (!missing.length) break;
    const deps = await Promise.all(missing.map(async (id) => {
      const info = await fetchJsonCached(`${MODRINTH}/project/${id}`, path.join(paths.cache, 'modrinth', `project_${id}.json`), 7 * 24 * 3600 * 1000).catch(() => null);
      const dep = info && await resolveModrinth(info.slug, mc).catch(() => null);
      if (dep) dep.dependency = true;
      return dep;
    }));
    for (const d of deps) if (d) plan.set(d.projectId, d);
  }

  // Drop mods whose required dependencies still did not resolve, until stable.
  let usable = [...plan.values()];
  for (;;) {
    const ids = new Set(usable.map((p) => p.projectId));
    const ok = usable.filter((p) => p.requires.every((id) => ids.has(id)));
    if (ok.length === usable.length) break;
    for (const p of usable) if (!ok.includes(p)) unavailable.push(CURATED.find((c) => c.slug === p.slug)?.name || p.slug);
    usable = ok;
  }

  const next = {};
  for (const p of usable) next[p.slug] = { file: p.filename, version: p.version };
  if (swiftMod) next.swiftclient = { file: path.basename(swiftMod), version: 'bundled' };

  // Remove curated files that changed or are no longer wanted.
  for (const [slug, info] of Object.entries(state.managed || {})) {
    if (!next[slug] || next[slug].file !== info.file) {
      try { fs.unlinkSync(path.join(dir, info.file)); } catch { /* already gone */ }
    }
  }

  await downloadAll(usable.map((p) => ({ url: p.url, file: path.join(dir, p.filename), sha512: p.sha512, size: p.size })),
    { onProgress, label: 'Mods' });
  if (swiftMod) fs.copyFileSync(swiftMod, path.join(dir, path.basename(swiftMod)));

  // Remember the mod ids each curated jar provides, for import conflict checks.
  for (const [slug, info] of Object.entries(next)) {
    const meta = readModMeta(path.join(dir, info.file));
    info.ids = [meta.id, ...(meta.provides || [])].filter(Boolean);
    info.name = meta.name || slug;
  }
  writeState(mc, { managed: next });
  return { installed: Object.keys(next), unavailable };
}

// ---- user mods --------------------------------------------------------------

function listMods(mc) {
  const dir = modsDir(mc);
  const state = readState(mc);
  const managedFiles = new Map(Object.entries(state.managed || {}).map(([slug, i]) => [i.file, { slug, ...i }]));
  let files = [];
  try { files = fs.readdirSync(dir).filter((f) => /\.jar(\.disabled)?$/i.test(f)); } catch { /* none yet */ }
  return files.map((file) => {
    const enabled = !file.endsWith('.disabled');
    const m = managedFiles.get(file);
    if (m) return { file, name: m.name || m.slug, version: m.version, managed: true, enabled: true };
    const meta = readModMeta(path.join(dir, file));
    return { file, name: meta.name || file, version: meta.version || '', id: meta.id, managed: false, enabled };
  }).sort((a, b) => (a.managed - b.managed) || a.name.localeCompare(b.name));
}

function managedIds(mc) {
  const ids = new Map();
  for (const [slug, info] of Object.entries(readState(mc).managed || {})) {
    for (const id of info.ids || [slug]) ids.set(id, info.name || slug);
  }
  return ids;
}

/** Checks a jar for import into `mc`. Returns {ok, meta, problems[], warnings[]} without copying. */
function checkMod(mc, jarPath) {
  const problems = [];
  const warnings = [];
  if (!/\.jar$/i.test(jarPath)) return { ok: false, problems: ['Only .jar files can be imported.'], warnings };
  const meta = readModMeta(jarPath);
  const kinds = {
    invalid: meta.error || 'This file is not a valid mod jar.',
    quilt: 'This is a Quilt-only mod. SwiftClient runs Fabric; look for the Fabric version of this mod.',
    forge: 'This is a Forge mod. SwiftClient runs Fabric; look for the Fabric version of this mod.',
    neoforge: 'This is a NeoForge mod. SwiftClient runs Fabric; look for the Fabric version of this mod.',
    unknown: 'No fabric.mod.json found: this is not a Fabric mod.',
  };
  if (kinds[meta.kind]) return { ok: false, meta, problems: [kinds[meta.kind]], warnings };

  if (INCOMPATIBLE[meta.id]) problems.push(INCOMPATIBLE[meta.id]);
  if (meta.environment === 'server') problems.push(`${meta.name} is a server-only mod.`);

  const mcOk = versionMatches(mc, meta.depends.minecraft);
  if (mcOk === false) problems.push(`${meta.name} ${meta.version} needs Minecraft ${[].concat(meta.depends.minecraft).join(' or ')}, but this profile is ${mc}.`);
  else if (mcOk === null) warnings.push(`Could not read which Minecraft versions ${meta.name} supports.`);

  const javaReq = meta.depends.java;
  if (javaReq && versionMatches('25', javaReq) === false && versionMatches('21', javaReq) === false) {
    warnings.push(`${meta.name} asks for Java ${javaReq}.`);
  }

  const managed = managedIds(mc);
  if (managed.has(meta.id)) problems.push(`${meta.name} is already included by SwiftClient (${managed.get(meta.id)}).`);
  for (const id of Object.keys(meta.breaks)) {
    if (managed.has(id)) problems.push(`${meta.name} declares it breaks ${managed.get(id)}, which SwiftClient includes.`);
  }

  // Missing dependencies are a warning: the user may import them next.
  const present = new Set([...managed.keys(), 'minecraft', 'java', 'fabricloader', 'fabric']);
  for (const m of listMods(mc)) if (m.id) present.add(m.id);
  const missing = Object.keys(meta.depends).filter((id) => !present.has(id) && !id.startsWith('fabric-'));
  if (missing.length) warnings.push(`${meta.name} also needs: ${missing.join(', ')}. Import those too or it will not load.`);

  return { ok: problems.length === 0, meta, problems, warnings };
}

function importMod(mc, jarPath) {
  const result = checkMod(mc, jarPath);
  if (!result.ok) return result;
  const dir = modsDir(mc);
  fs.mkdirSync(dir, { recursive: true });
  // Replace an older copy of the same mod.
  for (const m of listMods(mc)) {
    if (!m.managed && m.id && m.id === result.meta.id) fs.unlinkSync(path.join(dir, m.file));
  }
  fs.copyFileSync(jarPath, path.join(dir, path.basename(jarPath)));
  return result;
}

function setModEnabled(mc, file, enabled) {
  const dir = modsDir(mc);
  const base = file.replace(/\.disabled$/, '');
  const from = path.join(dir, file);
  const to = path.join(dir, enabled ? base : `${base}.disabled`);
  if (from !== to) fs.renameSync(from, to);
}

function removeMod(mc, file) {
  const state = readState(mc);
  if (Object.values(state.managed || {}).some((i) => i.file === file)) throw new Error('Built-in mods cannot be removed; turn them off in Settings instead.');
  fs.unlinkSync(path.join(modsDir(mc), file));
}

module.exports = {
  CURATED, syncMods, listMods, checkMod, importMod, setModEnabled, removeMod, readModMeta, versionMatches, modsDir,
};
