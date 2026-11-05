'use strict';
const test = require('node:test');
const assert = require('node:assert');
const crypto = require('crypto');
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

test('cape server verifies players, friends and one-time redeem codes', async () => {
  const http = require('http');
  const PROFILES = {
    Notch: '069a79f444e94726a5befca90e38aaf5',
    Alice: '1'.repeat(32),
    Bob: '2'.repeat(32),
    Carol: '3'.repeat(32),
    katleal: '5b79930d65184ca08b836bb77386cecf',
  };
  // Fake Mojang session server: every known name has joined with any serverId.
  const mojang = http.createServer((req, res) => {
    const u = new URL(req.url, 'http://x');
    const name = u.searchParams.get('username');
    if (PROFILES[name]) { res.writeHead(200); res.end(JSON.stringify({ id: PROFILES[name], name })); } else { res.writeHead(204); res.end(); }
  });
  await new Promise((r) => mojang.listen(0, '127.0.0.1', r));
  const capesDir = fs.mkdtempSync(path.join(os.tmpdir(), 'capes-'));
  fs.writeFileSync(path.join(capesDir, 'catalog.json'), JSON.stringify([
    { id: 'classic', name: 'Classic', public: true },
    { id: 'valenmc', name: 'ValenMC', public: true },
    { id: 'staff', name: 'Staff' },
  ]));
  fs.writeFileSync(path.join(capesDir, 'classic.png'), 'png');
  fs.writeFileSync(path.join(capesDir, 'valenmc.png'), 'png');
  process.env.CAPES_DIR = capesDir;
  process.env.DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'capedata-'));
  process.env.SESSION_SERVER = `http://127.0.0.1:${mojang.address().port}`;
  const { server } = require('../../cape-server/server');
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (p, body) => fetch(base + p, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body || {}) });
  const challenge = async () => (await (await post('/auth/challenge')).json()).serverId;
  const social = async (name, action, extra = {}) => post(`/social/${action}`, { name, serverId: await challenge(), ...extra });
  const admin = async (path, body) => post(path, { name: 'katleal', serverId: await challenge(), ...body });
  // Friend requests resolve names through Mojang; keep tests offline and deterministic.
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url, opts) => {
    const u = String(url);
    if (u.startsWith('https://api.mojang.com/users/profiles/minecraft/')) {
      const name = decodeURIComponent(u.split('/').pop());
      return PROFILES[name]
        ? new Response(JSON.stringify({ id: PROFILES[name], name }), { status: 200 })
        : new Response(null, { status: 404 });
    }
    return realFetch(url, opts);
  };

  try {
    assert.strictEqual((await post('/select', { name: 'Notch', serverId: await challenge(), cape: 'staff' })).status, 403, 'locked cape refused');
    const c2 = await challenge();
    assert.strictEqual((await post('/select', { name: 'Notch', serverId: c2, cape: 'classic' })).status, 200);
    assert.strictEqual((await post('/select', { name: 'Notch', serverId: c2, cape: 'classic' })).status, 400, 'challenge is single-use');
    assert.strictEqual((await post('/select', { name: 'Herobrine', serverId: await challenge(), cape: 'classic' })).status, 403, 'unverified player refused');

    const sel = await (await fetch(`${base}/players/069a79f4-44e9-4726-a5be-fca90e38aaf5.json`)).json();
    assert.deepStrictEqual(sel, { cape: 'classic', url: '/capes/classic.png' });
    assert.strictEqual((await fetch(`${base}/players/00000000000000000000000000000000.json`)).status, 404);

    // Friends: request, accept, friends-only chat, remove.
    assert.strictEqual((await social('Alice', 'request', { target: 'Bob' })).status, 200);
    const pending = await (await social('Bob', 'state')).json();
    assert.strictEqual(pending.requests.length, 1);
    assert.strictEqual(pending.requests[0].from.uuid, PROFILES.Alice);
    assert.strictEqual((await social('Alice', 'send', { friend: PROFILES.Bob, text: 'hi' })).status, 403, 'not friends yet');
    assert.strictEqual((await social('Bob', 'accept', { from: PROFILES.Alice })).status, 200);
    assert.strictEqual((await social('Alice', 'send', { friend: PROFILES.Bob, text: 'hello Bob' })).status, 200);
    const read = await (await social('Bob', 'messages', { friend: PROFILES.Alice })).json();
    assert.strictEqual(read.messages.length, 1);
    assert.strictEqual(read.messages[0].text, 'hello Bob');
    assert.strictEqual((await social('Carol', 'messages', { friend: PROFILES.Bob })).status, 403);
    assert.strictEqual((await social('Alice', 'send', { friend: PROFILES.Bob, text: 'x'.repeat(501) })).status, 400);
    const friends = await (await social('Bob', 'state')).json();
    assert.strictEqual(friends.friends[0].name, 'Alice');
    assert.strictEqual(friends.friends[0].online, true, 'a friend active on the Friends tab is online');
    assert.strictEqual((await social('Bob', 'remove', { friend: PROFILES.Alice })).status, 200);
    assert.strictEqual((await social('Alice', 'send', { friend: PROFILES.Bob, text: 'still there?' })).status, 403);
    assert.ok(fs.existsSync(path.join(process.env.DATA_DIR, 'social.json')), 'social state persists');

    // Redeem: a code can only be spent once by the same player, and that fact persists.
    const code = 'STAFF-TEST-1234';
    const hash = crypto.createHash('sha256').update(code).digest('hex');
    fs.writeFileSync(path.join(process.env.DATA_DIR, 'codes.json'), JSON.stringify({ [hash]: { cape: 'staff', uses: 0, redeemed: [] } }));
    const redeem = async () => post('/redeem', { name: 'Notch', serverId: await challenge(), code });
    assert.strictEqual((await redeem()).status, 200);
    assert.strictEqual((await redeem()).status, 409, 'the same player cannot redeem the code twice');
    const storedCodes = JSON.parse(fs.readFileSync(path.join(process.env.DATA_DIR, 'codes.json'), 'utf8'));
    assert.deepStrictEqual(storedCodes[hash].redeemed, [PROFILES.Notch], 'the redemption is written to disk');
    const grants = JSON.parse(fs.readFileSync(path.join(process.env.DATA_DIR, 'grants.json'), 'utf8'));
    assert.deepStrictEqual(grants[PROFILES.Notch], ['staff']);
    assert.strictEqual((await social('Notch', 'messages', { friend: PROFILES.Bob })).status, 403, 'strangers cannot read DMs');

    // Admin can hide a partner cape globally and remove it from selections/availability.
    assert.strictEqual((await admin('/admin/capes', { action: 'hide', cape: 'valenmc' })).status, 200);
    let publicCapes = await (await fetch(`${base}/capes`)).json();
    assert.ok(!publicCapes.some((c) => c.id === 'valenmc'), 'hidden cape disappears from the catalog');
    assert.strictEqual((await fetch(`${base}/capes/valenmc.png`)).status, 404, 'hidden cape texture is not served');
    const available = await (await fetch(`${base}/players/${'f'.repeat(32)}/available`)).json();
    assert.ok(!available.capes.includes('valenmc'), 'hidden cape is not available');
    const capeAdmin = await (await admin('/admin/capes', { action: 'list' })).json();
    assert.strictEqual(capeAdmin.capes.find((c) => c.id === 'valenmc').hidden, true);
    assert.strictEqual((await post('/admin/capes', { name: 'Notch', serverId: await challenge(), action: 'hide', cape: 'valenmc' })).status, 403, 'non-admin refused');
    assert.strictEqual((await admin('/admin/capes', { action: 'show', cape: 'valenmc' })).status, 200);

    // Admin can hide a partner server so launchers drop it and ServerPin removes stale entries.
    assert.deepStrictEqual((await (await fetch(`${base}/partners/hidden`)).json()).hidden, []);
    assert.strictEqual((await admin('/admin/partners', { action: 'hide', address: 'play.cyanrealms.com' })).status, 200);
    assert.deepStrictEqual((await (await fetch(`${base}/partners/hidden`)).json()).hidden, ['play.cyanrealms.com']);
    assert.strictEqual((await admin('/admin/partners', { action: 'show', address: 'play.cyanrealms.com' })).status, 200);
    assert.deepStrictEqual((await (await fetch(`${base}/partners/hidden`)).json()).hidden, []);
  } finally {
    globalThis.fetch = realFetch;
    server.close();
    mojang.close();
  }
});
test('cape Worker serves the catalog and verifies players through hasJoined', async () => {
  const { handle } = await import('../../cape-server/worker/src/index.js');
  const store = new Map([['grants', JSON.stringify({ '069a79f444e94726a5befca90e38aaf5': ['staff'] })]]);
  const env = {
    CAPES: {
      get: async (k, type) => (store.has(k) ? (type === 'json' ? JSON.parse(store.get(k)) : store.get(k)) : null),
      put: async (k, v) => { store.set(k, v); },
      delete: async (k) => { store.delete(k); },
    },
    ASSETS: { fetch: async (u) => new Response(`png:${new URL(u).pathname}`) },
    SESSION_SERVER: 'https://session.test',
  };
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (u) => (String(u).includes('username=Notch')
    ? new Response(JSON.stringify({ id: '069a79f444e94726a5befca90e38aaf5', name: 'Notch' }))
    : new Response(null, { status: 204 }));
  try {
    const req = (p, body) => handle(new Request(`https://capes.test${p}`, body ? { method: 'POST', body: JSON.stringify(body) } : {}), env);
    const json = async (p, body) => { const r = await req(p, body); return { status: r.status, body: await r.json() }; };
    const capes = (await json('/capes')).body;
    assert.ok(capes.some((c) => c.id === 'swift' && c.public));
    assert.strictEqual(await (await req('/capes/swift.png')).text(), 'png:/swift.png');
    assert.strictEqual((await req('/capes/nope.png')).status, 404);
    const challenge = async () => (await json('/auth/challenge', {})).body.serverId;
    assert.strictEqual((await json('/select', { name: 'Herobrine', serverId: await challenge(), cape: 'swift' })).status, 403, 'unverified player refused');
    assert.strictEqual((await json('/select', { name: 'Notch', serverId: 'f'.repeat(32), cape: 'swift' })).status, 400, 'unknown challenge refused');
    const id = await challenge();
    assert.strictEqual((await json('/select', { name: 'Notch', serverId: id, cape: 'staff' })).status, 200, 'granted cape allowed');
    assert.strictEqual((await json('/select', { name: 'Notch', serverId: id, cape: 'staff' })).status, 400, 'challenge is single-use');
    assert.deepStrictEqual((await json('/players/069a79f4-44e9-4726-a5be-fca90e38aaf5.json')).body, { cape: 'staff', url: '/capes/staff.png' });
    assert.strictEqual((await json('/select', { name: 'Notch', serverId: await challenge(), cape: 'founder' })).status, 403, 'locked cape refused');
    assert.strictEqual((await json('/players/069a79f444e94726a5befca90e38aaf5/available')).body.selected, 'staff');
  } finally {
    globalThis.fetch = realFetch;
  }
});
