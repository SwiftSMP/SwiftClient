'use strict';
// Temporary smoke run: installs, syncs mods, launches the game and stops it after a while.
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');
const minecraft = require('../src/main/minecraft');
const mods = require('../src/main/mods');
const { paths, loadSettings } = require('../src/main/config');
const appConfig = require('../swiftclient.config.json');

const mc = process.argv[2];
const seconds = Number(process.argv[3] || 90);
const logFile = process.argv[4] || `/tmp/mc-${mc}.log`;

(async () => {
  const profile = await minecraft.install(mc, () => {});
  const index = require('../../mod/dist/index.json');
  if (process.env.SKIP_SYNC !== '1') {
    await mods.syncMods(mc, loadSettings(), { swiftMod: path.join(__dirname, '..', '..', 'mod', 'dist', index[mc]), onProgress: () => {} });
  }
  const session = { name: 'SwiftTester', uuid: '00000000000000000000000000000000', accessToken: '0', xuid: '' };
  const cmd = minecraft.buildCommand(profile, { session, gameDir: paths.instance(mc), settings: loadSettings(), launcherVersion: 'smoke' });
  const i = cmd.args.indexOf(profile.mainClass);
  cmd.args.splice(i, 0,
    '-Dswiftclient.server.address=swiftsmp.net',
    '-Dswiftclient.server.name=SwiftSMP',
    `-Dswiftclient.capeApi=${process.env.CAPE_API || appConfig.capeApi || ''}`,
    `-Dswiftclient.debug=${process.env.SWIFT_DEBUG || ''}`);
  let out = '';
  const p = spawn(cmd.java.replace('javaw.exe', 'java.exe'), cmd.args, {
    cwd: paths.instance(mc),
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  p.stdout.on('data', (d) => { out += d; });
  p.stderr.on('data', (d) => { out += d; });
  const stop = setTimeout(() => { try { p.kill('SIGKILL'); } catch { /* gone */ } }, seconds * 1000);
  p.on('exit', (code) => {
    clearTimeout(stop);
    fs.writeFileSync(logFile, out);
    console.log(`exit ${code}, log ${logFile}`);
  });
})();
