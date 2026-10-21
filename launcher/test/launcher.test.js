'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const AdmZip = require('adm-zip');

// Keep the tests away from the real %APPDATA%\.swiftclient.
process.env.APPDATA = fs.mkdtempSync(path.join(os.tmpdir(), 'swiftclient-test-'));

const minecraft = require('../src/main/minecraft');
const mods = require('../src/main/mods');
const { parseGameOutput, prettyServer, activityFor } = require('../src/main/discord');
const { paths } = require('../src/main/config');

test('compareMc orders versions numerically', () => {
  assert.ok(minecraft.compareMc('1.21.10', '1.21.9') > 0);
  assert.ok(minecraft.compareMc('26.1', '1.21.11') > 0);
  assert.strictEqual(minecraft.compareMc('1.21', '1.21.0'), 0);
});

test('rulesAllow handles os, arch and features', () => {
  assert.strictEqual(minecraft.rulesAllow(undefined), true);
  assert.strictEqual(minecraft.rulesAllow([{ action: 'allow', os: { name: 'osx' } }]), false);
  assert.strictEqual(minecraft.rulesAllow([{ action: 'allow', os: { name: 'windows' } }]), true);
  assert.strictEqual(minecraft.rulesAllow([{ action: 'allow' }, { action: 'disallow', os: { name: 'windows' } }]), false);
  assert.strictEqual(minecraft.rulesAllow([{ action: 'allow', features: { has_custom_resolution: true } }], {}), false);
  assert.strictEqual(minecraft.rulesAllow([{ action: 'allow', features: { has_custom_resolution: true } }], { has_custom_resolution: true }), true);
});

test('mavenPath builds paths with classifiers', () => {
  assert.strictEqual(minecraft.mavenPath('net.fabricmc:fabric-loader:0.19.5'), 'net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar');
  assert.strictEqual(minecraft.mavenPath('org.lwjgl:lwjgl:3.4.3:natives-windows'), 'org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar');
});

test('expandArgs substitutes vars and applies rules', () => {
  const out = minecraft.expandArgs(['--user', '${auth_player_name}', { rules: [{ action: 'allow', features: { is_demo_user: true } }], value: '--demo' }], { auth_player_name: 'Steve' }, {});
  assert.deepStrictEqual(out, ['--user', 'Steve']);
});

test('versionMatches understands Fabric predicates', () => {
  const vm = mods.versionMatches;
  assert.strictEqual(vm('26.3', '~26.3'), true);
  assert.strictEqual(vm('26.2', '~26.3'), false);
  assert.strictEqual(vm('1.21.4', '>=1.21.2 <1.21.5'), true);
  assert.strictEqual(vm('1.21.5', '>=1.21.2 <1.21.5'), false);
  assert.strictEqual(vm('1.21.11', '1.21.x'), true);
  assert.strictEqual(vm('1.21', '1.21'), true);
  assert.strictEqual(vm('1.21.1', '1.21'), false);
  assert.strictEqual(vm('26.3', ['~26.2', '~26.3']), true);
  assert.strictEqual(vm('26.3', '>=1.21-'), true);
  assert.strictEqual(vm('26.3', '*'), true);
  assert.strictEqual(vm('26.3', undefined), true);
});

function makeJar(file, entries) {
  const zip = new AdmZip();
  for (const [name, content] of Object.entries(entries)) zip.addFile(name, Buffer.from(typeof content === 'string' ? content : JSON.stringify(content)));
  zip.writeZip(file);
  return file;
}

test('checkMod accepts a matching Fabric mod and rejects the wrong ones', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jars-'));
  const good = makeJar(path.join(dir, 'good.jar'), { 'fabric.mod.json': { id: 'goodmod', name: 'Good Mod', version: '1.0', depends: { minecraft: '~26.3' } } });
  const old = makeJar(path.join(dir, 'old.jar'), { 'fabric.mod.json': { id: 'oldmod', name: 'Old Mod', version: '1.0', depends: { minecraft: '1.20.1' } } });
  const forge = makeJar(path.join(dir, 'forge.jar'), { 'META-INF/mods.toml': 'modLoader="javafml"' });
  const optifine = makeJar(path.join(dir, 'of.jar'), { 'fabric.mod.json': { id: 'optifabric', name: 'OptiFabric', version: '1' } });
  const needsDep = makeJar(path.join(dir, 'dep.jar'), { 'fabric.mod.json': { id: 'depmod', name: 'Dep Mod', version: '1', depends: { minecraft: '*', cloth_config: '*' } } });

  assert.strictEqual(mods.checkMod('26.3', good).ok, true);
  const r = mods.checkMod('26.3', old);
  assert.strictEqual(r.ok, false);
  assert.match(r.problems[0], /needs Minecraft 1.20.1/);
  assert.match(mods.checkMod('26.3', forge).problems[0], /Forge mod/);
  assert.match(mods.checkMod('26.3', optifine).problems[0], /OptiFine/);
  const d = mods.checkMod('26.3', needsDep);
  assert.strictEqual(d.ok, true);
  assert.match(d.warnings[0], /cloth_config/);

  // Import, then a newer copy of the same mod replaces it.
  mods.importMod('26.3', good);
  const good2 = makeJar(path.join(dir, 'good-2.jar'), { 'fabric.mod.json': { id: 'goodmod', name: 'Good Mod', version: '2.0', depends: { minecraft: '~26.3' } } });
  mods.importMod('26.3', good2);
  const list = mods.listMods('26.3');
  assert.deepStrictEqual(list.map((m) => m.file), ['good-2.jar']);
  mods.setModEnabled('26.3', 'good-2.jar', false);
  assert.strictEqual(mods.listMods('26.3')[0].enabled, false);
});

test('checkMod refuses mods SwiftClient already manages', () => {
  const mc = '26.2';
  fs.mkdirSync(paths.instance(mc), { recursive: true });
  fs.writeFileSync(path.join(paths.instance(mc), 'swiftclient-mods.json'), JSON.stringify({ managed: { sodium: { file: 'sodium.jar', ids: ['sodium'], name: 'Sodium' } } }));
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'jars-'));
  const jar = makeJar(path.join(dir, 'sodium-old.jar'), { 'fabric.mod.json': { id: 'sodium', name: 'Sodium', version: '0.5', depends: { minecraft: '*' } } });
  assert.match(mods.checkMod(mc, jar).problems[0], /already included/);
});

test('parseGameOutput reads SwiftClient markers and vanilla fallbacks', () => {
  assert.deepStrictEqual(parseGameOutput('[12:00:00] [Render thread/INFO] [STDOUT]: [SwiftClient:presence] {"state":"multiplayer","server":"play.swiftsmp.net"}\n'),
    { phase: 'multiplayer', server: 'play.swiftsmp.net' });
  assert.deepStrictEqual(parseGameOutput('[Render thread/INFO]: Connecting to play.swiftsmp.net, 25565'), { phase: 'multiplayer', server: 'play.swiftsmp.net' });
  assert.deepStrictEqual(parseGameOutput('Starting integrated minecraft server version 26.3'), { phase: 'singleplayer', server: null });
  assert.strictEqual(parseGameOutput('nothing here'), null);
});

test('presence text matches the Lunar style', () => {
  assert.strictEqual(prettyServer('play.swiftsmp.net:25565'), 'swiftsmp.net');
  assert.strictEqual(activityFor({ phase: 'multiplayer', server: 'play.swiftsmp.net' }).details, 'Playing on swiftsmp.net');
  assert.strictEqual(activityFor({ phase: 'multiplayer', server: 'x.net' }, { showServer: false }).details, 'Playing Multiplayer');
});

test('cape server verifies players through hasJoined', async () => {
  const http = require('http');
  // Fake Mojang session server: "Notch" has joined with any serverId.
  const mojang = http.createServer((req, res) => {
    const u = new URL(req.url, 'http://x');
    if (u.searchParams.get('username') === 'Notch') { res.writeHead(200); res.end(JSON.stringify({ id: '069a79f444e94726a5befca90e38aaf5', name: 'Notch' })); } else { res.writeHead(204); res.end(); }
  });
  await new Promise((r) => mojang.listen(0, '127.0.0.1', r));
  const capesDir = fs.mkdtempSync(path.join(os.tmpdir(), 'capes-'));
  fs.writeFileSync(path.join(capesDir, 'catalog.json'), JSON.stringify([{ id: 'classic', name: 'Classic', public: true }, { id: 'staff', name: 'Staff' }]));
  fs.writeFileSync(path.join(capesDir, 'classic.png'), 'png');
  process.env.CAPES_DIR = capesDir;
  process.env.DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'capedata-'));
  process.env.SESSION_SERVER = `http://127.0.0.1:${mojang.address().port}`;
  const { server } = require('../../cape-server/server');
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (p, body) => fetch(base + p, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body || {}) });

  const { serverId } = await (await post('/auth/challenge')).json();
  assert.strictEqual((await post('/select', { name: 'Notch', serverId, cape: 'staff' })).status, 403, 'locked cape refused');
  const c2 = (await (await post('/auth/challenge')).json()).serverId;
  assert.strictEqual((await post('/select', { name: 'Notch', serverId: c2, cape: 'classic' })).status, 200);
  assert.strictEqual((await post('/select', { name: 'Notch', serverId: c2, cape: 'classic' })).status, 400, 'challenge is single-use');
  const c3 = (await (await post('/auth/challenge')).json()).serverId;
  assert.strictEqual((await post('/select', { name: 'Herobrine', serverId: c3, cape: 'classic' })).status, 403, 'unverified player refused');

  const sel = await (await fetch(`${base}/players/069a79f4-44e9-4726-a5be-fca90e38aaf5.json`)).json();
  assert.deepStrictEqual(sel, { cape: 'classic', url: '/capes/classic.png' });
  assert.strictEqual((await fetch(`${base}/players/00000000000000000000000000000000.json`)).status, 404);
  server.close();
  mojang.close();
});

test('offline names get the same UUID offline-mode servers assign', () => {
  const { offlineUuid, validOfflineName, offlineSession } = require('../src/main/offline');
  // Known value: UUID.nameUUIDFromBytes("OfflinePlayer:Notch")
  assert.strictEqual(offlineUuid('Notch'), 'b50ad385829d3141a2167e7d7539ba7f');
  assert.ok(validOfflineName('Steve_01'));
  assert.ok(!validOfflineName('ab'));
  assert.ok(!validOfflineName('has space'));
  assert.ok(!validOfflineName('a'.repeat(17)));
  assert.strictEqual(offlineSession('Steve').userType, 'legacy');
});
