'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const net = require('net');
const os = require('os');
const path = require('path');

process.env.APPDATA = fs.mkdtempSync(path.join(os.tmpdir(), 'swiftclient-test-'));

const { pingServer, splitAddress, flattenMotd } = require('../src/main/serverping');
const updates = require('../src/main/updates');

const NOTCH = { id: '069a79f444e94726a5befca90e38aaf5', name: 'Notch' };
// Minecraft access tokens are long; the cape server only accepts token-shaped strings.
const TOKEN = `valid-${'x'.repeat(40)}`;

function kvStore(initial = {}) {
  const map = new Map(Object.entries(initial));
  return {
    map,
    get: async (k, type) => (map.has(k) ? (type === 'json' ? JSON.parse(map.get(k)) : map.get(k)) : null),
    put: async (k, v) => { map.set(k, v); },
    delete: async (k) => { map.delete(k); },
    list: async ({ prefix = '' } = {}) => ({ keys: [...map.keys()].filter((k) => k.startsWith(prefix)).map((name) => ({ name })), list_complete: true }),
  };
}

function workerEnv(store) {
  return {
    CAPES: store,
    ASSETS: { fetch: async (u) => new Response(`png:${new URL(u).pathname}`) },
    SESSION_SERVER: 'https://session.test',
  };
}

test('cape Worker retries with the access token when Mojang cannot confirm the session', async () => {
  const { handle } = await import('../../cape-server/worker/src/index.js');
  const store = kvStore({ grants: JSON.stringify({ [NOTCH.id]: ['staff'] }) });
  const env = workerEnv(store);
  const realFetch = globalThis.fetch;
  const seen = [];
  globalThis.fetch = async (url, opts = {}) => {
    seen.push(String(url));
    if (String(url).includes('hasJoined')) return new Response(null, { status: 204 });
    if (String(url).includes('api.minecraftservices.com')) {
      const ok = (opts.headers && opts.headers.Authorization) === `Bearer ${TOKEN}`;
      return ok ? new Response(JSON.stringify(NOTCH)) : new Response(null, { status: 401 });
    }
    return new Response(null, { status: 204 });
  };
  try {
    const req = (p, body) => handle(new Request(`https://capes.test${p}`, body ? { method: 'POST', body: JSON.stringify(body) } : {}), env);
    const json = async (p, body) => { const r = await req(p, body); return { status: r.status, body: await r.json() }; };
    const challenge = async () => (await json('/auth/challenge', {})).body.serverId;
    const serverId = await challenge();

    const denied = await json('/select', { name: 'Notch', serverId, cape: 'swift' });
    assert.strictEqual(denied.status, 403);
    assert.strictEqual(denied.body.verify, 'failed');

    // The challenge survives a failed verification so the launcher can retry it with a token.
    const retried = await json('/select', { name: 'Notch', serverId, cape: 'swift', accessToken: TOKEN });
    assert.strictEqual(retried.status, 200);
    assert.strictEqual(retried.body.uuid, NOTCH.id);

    const state = await json(`/players/${NOTCH.id}.json`);
    assert.deepStrictEqual(state.body, { cape: 'swift', url: '/capes/swift.png' });
    assert.ok(seen.some((u) => u.includes('api.minecraftservices.com')));
  } finally {
    globalThis.fetch = realFetch;
  }
});

test('cape Worker refuses a bad token and keeps the challenge until it is used', async () => {
  const { handle } = await import('../../cape-server/worker/src/index.js');
  const store = kvStore();
  const env = workerEnv(store);
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url) => (String(url).includes('hasJoined') ? new Response(null, { status: 204 }) : new Response(null, { status: 401 }));
  try {
    const req = (p, body) => handle(new Request(`https://capes.test${p}`, body ? { method: 'POST', body: JSON.stringify(body) } : {}), env);
    const json = async (p, body) => { const r = await req(p, body); return { status: r.status, body: await r.json() }; };
    const serverId = (await json('/auth/challenge', {})).body.serverId;
    const bad = `bad-${'y'.repeat(40)}`;
    assert.strictEqual((await json('/select', { name: 'Notch', serverId, cape: 'swift', accessToken: bad })).status, 403);
    assert.strictEqual((await json('/select', { name: 'Notch', serverId, cape: 'swift', accessToken: bad })).status, 403, 'challenge is only spent on success');
  } finally {
    globalThis.fetch = realFetch;
  }
});

test('cape Worker redeems a code once per player and only for a verified session', async () => {
  const { handle } = await import('../../cape-server/worker/src/index.js');
  const crypto = require('crypto');
  const hash = crypto.createHash('sha256').update('STAFF-TEST-1234').digest('hex');
  const store = kvStore({ codes: JSON.stringify({ [hash]: { cape: 'staff', uses: 0, redeemed: [] } }) });
  const env = workerEnv(store);
  const realFetch = globalThis.fetch;
  globalThis.fetch = async (url, opts = {}) => {
    if (String(url).includes('hasJoined')) return new Response(null, { status: 204 });
    if (String(url).includes('api.minecraftservices.com')) {
      return (opts.headers && opts.headers.Authorization) === `Bearer ${TOKEN}`
        ? new Response(JSON.stringify(NOTCH)) : new Response(null, { status: 401 });
    }
    return new Response(null, { status: 204 });
  };
  try {
    const req = (p, body) => handle(new Request(`https://capes.test${p}`, body ? { method: 'POST', body: JSON.stringify(body) } : {}), env);
    const json = async (p, body) => { const r = await req(p, body); return { status: r.status, body: await r.json() }; };
    const challenge = async () => (await json('/auth/challenge', {})).body.serverId;

    // An unverified player cannot redeem, and the code is not spent.
    const unverified = await json('/redeem', { name: 'Notch', serverId: await challenge(), code: 'STAFF-TEST-1234' });
    assert.strictEqual(unverified.status, 403);

    const ok = await json('/redeem', { name: 'Notch', serverId: await challenge(), code: 'staff-test-1234', accessToken: TOKEN });
    assert.strictEqual(ok.status, 200);
    assert.strictEqual(ok.body.cape, 'staff');

    // The same player cannot redeem it again, and a wrong code is refused.
    assert.strictEqual((await json('/redeem', { name: 'Notch', serverId: await challenge(), code: 'STAFF-TEST-1234', accessToken: TOKEN })).status, 409);
    assert.strictEqual((await json('/redeem', { name: 'Notch', serverId: await challenge(), code: 'NOPE-NOPE-NOPE', accessToken: TOKEN })).status, 404);

    // The cape is now unlocked for the player.
    const available = await json(`/players/${NOTCH.id}/available`);
    assert.ok(available.body.capes.includes('staff'));
    // The raw code is never stored, only its hash.
    assert.ok(!JSON.stringify([...store.map.values()]).includes('STAFF-TEST-1234'));
  } finally {
    globalThis.fetch = realFetch;
  }
});

test('cape Worker counts installs and online launchers', async () => {
  const { handle } = await import('../../cape-server/worker/src/index.js');
  const env = workerEnv(kvStore());
  const post = async (p, body) => (await handle(new Request(`https://capes.test${p}`, { method: 'POST', body: JSON.stringify(body) }), env)).json();
  const id = 'a'.repeat(32);
  const first = await post('/stats/heartbeat', { id, version: '0.2.0' });
  assert.ok(first.online >= 1);
  const before = first.users;
  const again = await post('/stats/heartbeat', { id, version: '0.2.0' });
  assert.strictEqual(again.users, before, 'the same install is not counted twice');
  const second = await post('/stats/heartbeat', { id: 'b'.repeat(32), version: '0.2.0' });
  assert.strictEqual(second.users, before + 1);
  assert.strictEqual((await post('/stats/heartbeat', { id: 'nope' })).error, 'bad install id');
});

test('node cape server counts installs and exposes them on /stats', async () => {
  const http = require('http');
  const mojang = http.createServer((req, res) => { res.writeHead(204); res.end(); });
  await new Promise((r) => mojang.listen(0, '127.0.0.1', r));
  const capesDir = fs.mkdtempSync(path.join(os.tmpdir(), 'capes-stats-'));
  fs.writeFileSync(path.join(capesDir, 'catalog.json'), JSON.stringify([{ id: 'swift', name: 'Swift', public: true }]));
  fs.writeFileSync(path.join(capesDir, 'swift.png'), 'png');
  process.env.CAPES_DIR = capesDir;
  process.env.DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'capedata-stats-'));
  process.env.SESSION_SERVER = `http://127.0.0.1:${mojang.address().port}`;
  const { server } = require('../../cape-server/server');
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (p, body) => fetch(base + p, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body || {}) });
  try {
    const id = 'c'.repeat(32);
    const beat = await (await post('/stats/heartbeat', { id, version: '0.2.0' })).json();
    assert.strictEqual(beat.users, 1);
    assert.strictEqual(beat.online, 1);
    const stats = await (await fetch(`${base}/stats`)).json();
    assert.deepStrictEqual(stats, { users: 1, online: 1 });
    assert.ok(fs.existsSync(path.join(process.env.DATA_DIR, 'installs.json')));
  } finally {
    server.close();
    mojang.close();
  }
});

test('server ping reads players, motd and latency from a real handshake', async () => {
  const varInt = (n) => {
    const out = [];
    do { let b = n & 0x7f; n >>>= 7; if (n) b |= 0x80; out.push(b); } while (n);
    return Buffer.from(out);
  };
  const status = { version: { name: '26.3', protocol: 772 }, players: { online: 7, max: 50, sample: [{ name: 'Steve', id: 'x' }] }, description: { text: 'Hello ', extra: [{ text: 'SwiftSMP' }] } };
  const body = Buffer.from(JSON.stringify(status), 'utf8');
  const payload = Buffer.concat([varInt(0), varInt(body.length), body]);
  const frame = Buffer.concat([varInt(payload.length), payload]);
  const fake = net.createServer((socket) => {
    socket.once('data', () => socket.write(frame));
  });
  await new Promise((r) => fake.listen(0, '127.0.0.1', r));
  try {
    const port = fake.address().port;
    const result = await pingServer(`127.0.0.1:${port}`, { srv: false });
    assert.strictEqual(result.players.online, 7);
    assert.strictEqual(result.players.max, 50);
    assert.strictEqual(result.motd, 'Hello SwiftSMP');
    assert.strictEqual(result.version.name, '26.3');
    assert.ok(result.latencyMs >= 0);
  } finally {
    fake.close();
  }
  await assert.rejects(() => pingServer('127.0.0.1:1', { srv: false, timeoutMs: 2000 }), /refused|closed|answer/i);
});

test('verified downloads are cached by size and modification time', async () => {
  const { isValid, saveVerified } = require('../src/main/download');
  const { paths } = require('../src/main/config');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'verified-'));
  const file = path.join(dir, 'lib.jar');
  fs.writeFileSync(file, 'abc');
  const sha1 = require('crypto').createHash('sha1').update('abc').digest('hex');
  assert.strictEqual(await isValid(file, { sha1, size: 3 }), true);
  saveVerified();
  assert.ok(fs.existsSync(path.join(paths.cache, 'verified.json')), 'cache file is written');
  // Same size, different bytes and a newer mtime: the cache must not wave it through.
  const later = new Date(Date.now() + 5000);
  fs.writeFileSync(file, 'abd');
  fs.utimesSync(file, later, later);
  assert.strictEqual(await isValid(file, { sha1, size: 3 }), false);
});

test('splitAddress and flattenMotd handle the edge cases', () => {
  assert.deepStrictEqual(splitAddress('swiftsmp.net'), { host: 'swiftsmp.net', port: 25565 });
  assert.deepStrictEqual(splitAddress('play.example.com:25566'), { host: 'play.example.com', port: 25566 });
  assert.deepStrictEqual(splitAddress('[::1]:25567'), { host: '::1', port: 25567 });
  assert.throws(() => splitAddress(''));
  assert.strictEqual(flattenMotd('plain'), 'plain');
  assert.strictEqual(flattenMotd(['a', { text: 'b' }]), 'ab');
  assert.strictEqual(flattenMotd(undefined), '');
});

test('update helpers compare versions and pick the installer', async () => {
  assert.strictEqual(updates.parseTag('v0.2.0'), '0.2.0');
  assert.strictEqual(updates.parseTag('nightly'), null);
  assert.strictEqual(updates.isNewer('0.2.0', '0.1.4'), true);
  assert.strictEqual(updates.isNewer('0.1.4', '0.1.4'), false);
  assert.strictEqual(updates.isNewer('garbage', '0.1.4'), false);

  const release = {
    version: '0.2.0',
    url: 'https://github.com/SwiftSMP/SwiftClient/releases/tag/v0.2.0',
    assets: [
      { name: 'latest.yml', url: 'https://example.test/latest.yml', size: 1 },
      { name: 'SwiftClient-Setup.exe', url: 'https://example.test/SwiftClient-Setup.exe', size: 2 },
    ],
  };
  assert.strictEqual(await updates.installerUrl(release), 'https://example.test/SwiftClient-Setup.exe');
  assert.strictEqual(await updates.installerUrl({ ...release, assets: [] }), release.url);

  const fakeFetch = async (url) => {
    assert.match(url, /repos\/SwiftSMP\/SwiftClient\/releases\/latest/);
    return { ok: true, json: async () => ({ tag_name: 'v0.2.0', html_url: release.url, body: 'notes', assets: release.assets.map((a) => ({ name: a.name, browser_download_url: a.url, size: a.size })) }) };
  };
  const parsed = await updates.latestRelease({ fetchImpl: fakeFetch });
  assert.strictEqual(parsed.version, '0.2.0');
  assert.strictEqual(parsed.assets.length, 2);

  await assert.rejects(
    () => updates.latestRelease({ fetchImpl: async () => ({ ok: false, status: 404, json: async () => ({}) }) }),
    /HTTP 404/);
});
