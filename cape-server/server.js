'use strict';
// SwiftClient cape server. Zero dependencies, Node 18+.
//
//   GET  /capes                      catalog [{id, name, description, public}]
//   GET  /capes/<id>.png             cape texture (64x32)
//   GET  /players/<uuid>.json        {cape, url} for a player, 404 if none
//   GET  /players/<uuid>/available   capes this player may pick
//   GET  /stats                      {users, online} SwiftClient installs / online launchers
//   POST /stats/heartbeat {id,version}  records a running launcher
//   POST /auth/challenge             {serverId} one-time challenge
//   POST /select {name, serverId, cape, accessToken?}
//        Player proves ownership the same way a Minecraft server does: the launcher
//        calls Mojang's session "join" with serverId, we verify with "hasJoined".
//        When that check fails (Mojang ties the session to the player's IP) the launcher
//        retries with its Minecraft access token, which is verified with Mojang and never
//        stored or logged.
//
// Data: capes/catalog.json + capes/*.png (shipped), data/grants.json
// ({"<uuid>": ["staff", ...]}) for non-public capes, data/selections.json (written),
// data/installs.json (anonymous install ids, written).

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 8765);
const HOST = process.env.HOST || '127.0.0.1';
const CAPES_DIR = process.env.CAPES_DIR || path.join(__dirname, 'capes');
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const SESSION_SERVER = process.env.SESSION_SERVER || 'https://sessionserver.mojang.com';
const PROFILE_URL = process.env.PROFILE_URL || 'https://api.minecraftservices.com/minecraft/profile';
const USER_AGENT = 'SwiftClient-Capes/1.1 (+https://swiftsmp.com)';
const PRESENCE_TTL_MS = 25 * 60_000;

fs.mkdirSync(DATA_DIR, { recursive: true });
const selectionsFile = path.join(DATA_DIR, 'selections.json');
const grantsFile = path.join(DATA_DIR, 'grants.json');
const installsFile = path.join(DATA_DIR, 'installs.json');

const readJson = (file, fallback) => { try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return fallback; } };
const writeJson = (file, value) => {
  const tmp = `${file}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2));
  fs.renameSync(tmp, file);
};
// Redeem codes: only SHA-256 hashes are stored, so the file leaks nothing usable.
const codesFile = path.join(DATA_DIR, 'codes.json');
const codes = () => readJson(codesFile, {});
// Players allowed to create and list redeem codes (katleal).
const ADMIN_UUIDS = ['5b79930d65184ca08b836bb77386cecf'];
// No 0/O or 1/I, so codes are easy to read aloud. 32 symbols divide 256 evenly, so no bias.
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const newCode = () => {
  const c = [...crypto.randomBytes(12)].map((b) => CODE_ALPHABET[b % 32]).join('');
  return `${c.slice(0, 4)}-${c.slice(4, 8)}-${c.slice(8, 12)}`;
};

function writeCodes(value) {
  const tmp = `${codesFile}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(value, null, 2));
  fs.renameSync(tmp, codesFile);
}
const catalog = () => readJson(path.join(CAPES_DIR, 'catalog.json'), []);
let selections = readJson(selectionsFile, {});

function saveSelections() {
  const tmp = `${selectionsFile}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(selections, null, 2));
  fs.renameSync(tmp, selectionsFile);
}

const normUuid = (u) => String(u || '').toLowerCase().replace(/-/g, '');
const isUuid = (u) => /^[0-9a-f]{32}$/.test(u);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function available(uuid) {
  const granted = new Set((readJson(grantsFile, {})[uuid] || []));
  return catalog().filter((c) => c.public || granted.has(c.id));
}

// Anonymous installs (written once per launcher) and presence (refreshed by heartbeats).
let installs = readJson(installsFile, {});
const presence = new Map();

// ---- identity ---------------------------------------------------------------

/** Mojang's own check: the launcher called session/minecraft/join with this serverId. */
async function handshakeProfile(name, serverId) {
  const query = new URLSearchParams({ username: name, serverId });
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const r = await fetch(`${SESSION_SERVER}/session/minecraft/hasJoined?${query}`, {
        headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
      });
      if (r.status === 200) {
        const profile = await r.json();
        if (isUuid(normUuid(profile.id))) return profile;
      }
    } catch { /* network hiccup: try once more */ }
    if (attempt === 0) await sleep(250);
  }
  return null;
}

/** Fallback proof: Mojang confirms the token belongs to a real Minecraft account. */
async function tokenProfile(accessToken) {
  if (typeof accessToken !== 'string' || !/^[A-Za-z0-9._~+/=-]{20,4096}$/.test(accessToken)) return null;
  try {
    const r = await fetch(PROFILE_URL, {
      headers: { Authorization: `Bearer ${accessToken}`, 'User-Agent': USER_AGENT, Accept: 'application/json' },
    });
    if (r.status !== 200) return null;
    const profile = await r.json();
    return isUuid(normUuid(profile.id)) ? profile : null;
  } catch { return null; }
}

async function verifyPlayer({ name, serverId, accessToken }) {
  const profile = (await handshakeProfile(name, serverId)) || (await tokenProfile(accessToken));
  if (!profile) return null;
  return { uuid: normUuid(profile.id), name: String(profile.name || name) };
}

function stats() {
  const now = Date.now();
  let online = 0;
  for (const [id, seen] of presence) {
    if (now - seen > PRESENCE_TTL_MS) presence.delete(id);
    else online++;
  }
  return { users: Object.keys(installs).length, online };
}

function heartbeat(body) {
  const id = normUuid(body.id);
  if (!isUuid(id)) return null;
  if (!installs[id]) {
    installs[id] = { first: new Date().toISOString(), version: String(body.version || '').slice(0, 32) };
    const tmp = `${installsFile}.tmp`;
    fs.writeFileSync(tmp, JSON.stringify(installs, null, 2));
    fs.renameSync(tmp, installsFile);
  }
  presence.set(id, Date.now());
  return stats();
}

// One-time challenges, valid for 2 minutes.
const challenges = new Map();
setInterval(() => { const now = Date.now(); for (const [k, t] of challenges) if (t < now) challenges.delete(k); }, 30000).unref();

// Basic per-IP rate limit for POSTs.
const hits = new Map();
function limited(ip) {
  const now = Date.now();
  const h = (hits.get(ip) || []).filter((t) => now - t < 60000);
  h.push(now);
  hits.set(ip, h);
  return h.length > 30;
}

function send(res, status, body, headers = {}) {
  const isBuf = Buffer.isBuffer(body);
  res.writeHead(status, {
    'Content-Type': isBuf ? 'image/png' : 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Cache-Control': isBuf ? 'public, max-age=3600' : 'no-cache',
    ...headers,
  });
  res.end(isBuf ? body : JSON.stringify(body));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => { data += c; if (data.length > 4096) { reject(new Error('too large')); req.destroy(); } });
    req.on('end', () => { try { resolve(JSON.parse(data || '{}')); } catch { reject(new Error('bad json')); } });
  });
}

async function handle(req, res) {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname.replace(/\/+$/, '') || '/';
  const ip = req.headers['x-forwarded-for']?.split(',')[0].trim() || req.socket.remoteAddress;

  if (req.method === 'GET') {
    if (p === '/' || p === '/health') return send(res, 200, { ok: true, capes: catalog().length });
    if (p === '/capes') return send(res, 200, catalog().map(({ id, name, description, public: pub }) => ({ id, name, description, public: !!pub })));
    if (p === '/stats') return send(res, 200, stats(), { 'Cache-Control': 'public, max-age=60' });
    let m = p.match(/^\/capes\/([a-z0-9_-]+)\.png$/);
    if (m) {
      const cape = catalog().find((c) => c.id === m[1]);
      if (!cape) return send(res, 404, { error: 'unknown cape' });
      return send(res, 200, fs.readFileSync(path.join(CAPES_DIR, `${cape.id}.png`)));
    }
    m = p.match(/^\/players\/([0-9a-fA-F-]{32,36})\.json$/);
    if (m) {
      const uuid = normUuid(m[1]);
      const sel = selections[uuid];
      if (!sel || !catalog().some((c) => c.id === sel.cape)) return send(res, 404, { error: 'no cape' });
      return send(res, 200, { cape: sel.cape, url: `/capes/${sel.cape}.png` }, { 'Cache-Control': 'public, max-age=60' });
    }
    m = p.match(/^\/players\/([0-9a-fA-F-]{32,36})\/available$/);
    if (m) {
      const uuid = normUuid(m[1]);
      return send(res, 200, { selected: selections[uuid]?.cape || null, capes: available(uuid).map((c) => c.id) });
    }
    return send(res, 404, { error: 'not found' });
  }

  if (req.method === 'POST') {
    if (limited(ip)) return send(res, 429, { error: 'slow down' });
    if (p === '/auth/challenge') {
      const serverId = crypto.randomBytes(16).toString('hex');
      challenges.set(serverId, Date.now() + 120000);
      return send(res, 200, { serverId });
    }
    if (p === '/stats/heartbeat') {
      let body;
      try { body = await readBody(req); } catch (e) { return send(res, 400, { error: e.message }); }
      const result = heartbeat(body);
      return result ? send(res, 200, result) : send(res, 400, { error: 'bad install id' });
    }
    if (p === '/redeem') {
      let body;
      try { body = await readBody(req); } catch (e) { return send(res, 400, { error: e.message }); }
      const { name, serverId, code, accessToken } = body;
      if (!challenges.has(serverId)) return send(res, 400, { error: 'unknown or expired challenge' });
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name || '')) return send(res, 400, { error: 'bad name' });
      if (typeof code !== 'string' || !/^[A-Za-z0-9-]{6,64}$/.test(code)) return send(res, 400, { error: 'bad code' });

      const player = await verifyPlayer({ name, serverId, accessToken });
      if (!player) return send(res, 403, { error: 'Mojang could not verify this session', verify: 'failed' });
      challenges.delete(serverId);

      const hash = crypto.createHash('sha256').update(code.toUpperCase()).digest('hex');
      const entry = codes()[hash];
      if (!entry) return send(res, 404, { error: 'That code is not valid.' });
      if (entry.uses && (entry.redeemed || []).length >= entry.uses) return send(res, 409, { error: 'That code has already been used.' });
      if ((entry.redeemed || []).includes(player.uuid)) return send(res, 409, { error: 'You already have this cape.' });

      const grants = readJson(grantsFile, {});
      grants[player.uuid] = [...new Set([...(grants[player.uuid] || []), entry.cape])];
      writeJson(grantsFile, grants);
      entry.redeemed = [...(entry.redeemed || []), player.uuid];
      writeJson(codesFile, codes());
      return send(res, 200, { ok: true, uuid: player.uuid, cape: entry.cape });
    }
    if (p === '/admin/codes') {
      let body;
      try { body = await readBody(req); } catch (e) { return send(res, 400, { error: e.message }); }
      const { name, serverId, accessToken, action, cape, uses, count } = body;
      if (!challenges.has(serverId)) return send(res, 400, { error: 'unknown or expired challenge' });
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name || '')) return send(res, 400, { error: 'bad name' });

      const player = await verifyPlayer({ name, serverId, accessToken });
      if (!player) return send(res, 403, { error: 'Mojang could not verify this session', verify: 'failed' });
      challenges.delete(serverId);
      if (!ADMIN_UUIDS.includes(player.uuid)) return send(res, 403, { error: 'not an admin' });

      const all = codes();
      if (action === 'list') {
        return send(res, 200, {
          codes: Object.values(all).map((e) => ({ cape: e.cape, uses: e.uses, redeemed: (e.redeemed || []).length, created: e.created || null })),
        });
      }
      if (action !== 'create') return send(res, 400, { error: 'bad action' });
      if (!catalog().some((c) => c.id === cape && !c.public)) return send(res, 400, { error: 'bad cape: must be a non-public cape' });
      const n = count === undefined ? 1 : Number(count);
      if (!Number.isInteger(n) || n < 1 || n > 100) return send(res, 400, { error: 'count must be 1 to 100' });
      const u = uses === undefined ? 1 : Number(uses);
      if (!Number.isInteger(u) || u < 0 || u > 10000) return send(res, 400, { error: 'uses must be 0 (unlimited) to 10000' });

      const made = [];
      for (let i = 0; i < n; i++) {
        let code;
        let hash;
        do {
          code = newCode();
          hash = crypto.createHash('sha256').update(code).digest('hex');
        } while (all[hash]);
        all[hash] = { cape, uses: u, redeemed: [], created: new Date().toISOString() };
        made.push(code);
      }
      writeCodes(all);
      return send(res, 200, { ok: true, cape, uses: u, codes: made });
    }
    if (p === '/select') {
      let body;
      try { body = await readBody(req); } catch (e) { return send(res, 400, { error: e.message }); }
      const { name, serverId, cape, accessToken } = body;
      if (!challenges.has(serverId)) return send(res, 400, { error: 'unknown or expired challenge' });
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name || '')) return send(res, 400, { error: 'bad name' });

      const player = await verifyPlayer({ name, serverId, accessToken });
      // Keep the challenge until it is actually used: the launcher retries it with its token.
      if (!player) return send(res, 403, { error: 'Mojang could not verify this session', verify: 'failed' });
      challenges.delete(serverId);

      if (cape === null || cape === '') {
        delete selections[player.uuid];
      } else {
        if (!available(player.uuid).some((c) => c.id === cape)) return send(res, 403, { error: 'you do not have this cape' });
        selections[player.uuid] = { cape, name: player.name, at: new Date().toISOString() };
      }
      saveSelections();
      return send(res, 200, { ok: true, uuid: player.uuid, cape: selections[player.uuid]?.cape || null });
    }
    return send(res, 404, { error: 'not found' });
  }

  if (req.method === 'OPTIONS') return send(res, 204, {}, { 'Access-Control-Allow-Methods': 'GET, POST', 'Access-Control-Allow-Headers': 'Content-Type' });
  return send(res, 405, { error: 'method not allowed' });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch((e) => { console.error(e); if (!res.headersSent) send(res, 500, { error: 'internal error' }); });
});

if (require.main === module) {
  server.listen(PORT, HOST, () => console.log(`SwiftClient cape server on http://${HOST}:${PORT}`));
}

module.exports = { server };
