'use strict';
// Installs a version + curated mods without the UI and prints the launch command
// (with a fake session). Usage: node scripts/smoke-install.js [mc] [--run]
const path = require('path');
const { spawn } = require('child_process');
const minecraft = require('../src/main/minecraft');
const mods = require('../src/main/mods');
const { paths, loadSettings } = require('../src/main/config');

(async () => {
  const versions = await minecraft.listVersions('1.21');
  const mc = process.argv[2] || versions[0];
  console.log('versions:', versions.join(', '));
  let last = '';
  const progress = (d, t, l) => { const s = `${l} ${d}/${t}`; if (l !== last || d === t) { console.log(s); last = l; } };
  const t0 = Date.now();
  const profile = await minecraft.install(mc, progress);
  const swiftMod = process.env.SWIFT_MOD || path.join(__dirname, '..', '..', 'mod', 'dist', (require('../../mod/dist/index.json'))[mc] || 'none.jar');
  const r = await mods.syncMods(mc, loadSettings(), { swiftMod, onProgress: progress });
  console.log('mods installed:', r.installed.join(', '));
  console.log('unavailable:', r.unavailable.join(', ') || '-');
  console.log(`install took ${((Date.now() - t0) / 1000).toFixed(1)}s`);
  // Offline-mode session: enough to reach the title screen, not to join online servers.
  const session = { name: 'SwiftTester', uuid: '00000000000000000000000000000000', accessToken: '0', xuid: '' };
  const cmd = minecraft.buildCommand(profile, { session, gameDir: paths.instance(mc), settings: loadSettings(), launcherVersion: 'smoke' });
  cmd.args.splice(cmd.args.indexOf(profile.mainClass), 0, '-Dswiftclient.server.address=swiftsmp.net', '-Dswiftclient.server.name=SwiftSMP');
  if (process.env.CAPE_API) cmd.args.splice(cmd.args.indexOf(profile.mainClass), 0, `-Dswiftclient.capeApi=${process.env.CAPE_API}`);
  if (process.env.SWIFT_DEBUG) cmd.args.splice(cmd.args.indexOf(profile.mainClass), 0, `-Dswiftclient.debug=${process.env.SWIFT_DEBUG}`);
  if (process.env.QUICKPLAY_WORLD) cmd.args.push('--quickPlaySingleplayer', process.env.QUICKPLAY_WORLD);
  console.log(cmd.java, cmd.args.filter((a) => !a.includes(path.delimiter)).join(' '));
  if (process.argv.includes('--run')) {
    const p = spawn(cmd.java.replace('javaw.exe', 'java.exe'), cmd.args, { cwd: paths.instance(mc), stdio: 'inherit' });
    p.on('exit', (c) => console.log('exit', c));
  }
})().catch((e) => { console.error('FAILED', e); process.exit(1); });
