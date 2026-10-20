'use strict';
// SwiftClient cape server. Zero dependencies, Node 18+.
//
//   GET  /capes                      catalog [{id, name, description, public}]
//   GET  /capes/<id>.png             cape texture (64x32)
//   GET  /players/<uuid>.json        {cape, url} for a player, 404 if none
//   GET  /players/<uuid>/available   capes this player may pick
//   POST /auth/challenge             {serverId} one-time challenge
//   POST /select {name, serverId, cape}
//        Player proves ownership the same way a Minecraft server does: the launcher
//        calls Mojang's session "join" with serverId, we verify with "hasJoined".
//        The player's access token never reaches this server.
//
// Data: capes/catalog.json + capes/*.png (shipped), data/grants.json
// ({"<uuid>": ["staff", ...]}) for non-public capes, data/selections.json (written).

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = Number(process.env.PORT || 8765);
const HOST = process.env.HOST || '127.0.0.1';
const CAPES_DIR = process.env.CAPES_DIR || path.join(__dirname, 'capes');
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const SESSION_SERVER = process.env.SESSION_SERVER || 'https://sessionserver.mojang.com';

fs.mkdirSync(DATA_DIR, { recursive: true });
const selectionsFile = path.join(DATA_DIR, 'selections.json');
const grantsFile = path.join(DATA_DIR, 'grants.json');

const readJson = (file, fallback) => { try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return fallback; } };
const catalog = () => readJson(path.join(CAPES_DIR, 'catalog.json'), []);
let selections = readJson(selectionsFile, {});

function saveSelections() {
  const tmp = `${selectionsFile}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(selections, null, 2));
  fs.renameSync(tmp, selectionsFile);
}

const normUuid = (u) => String(u || '').toLowerCase().replace(/-/g, '');
const isUuid = (u) => /^[0-9a-f]{32}$/.test(u);

function available(uuid) {
  const granted = new Set((readJson(grantsFile, {})[uuid] || []));
  return catalog().filter((c) => c.public || granted.has(c.id));
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
    if (p === '/select') {
      let body;
      try { body = await readBody(req); } catch (e) { return send(res, 400, { error: e.message }); }
      const { name, serverId, cape } = body;
      if (!challenges.has(serverId)) return send(res, 400, { error: 'unknown or expired challenge' });
      challenges.delete(serverId);
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name || '')) return send(res, 400, { error: 'bad name' });
      const r = await fetch(`${SESSION_SERVER}/session/minecraft/hasJoined?` + new URLSearchParams({ username: name, serverId }));
      if (r.status !== 200) return send(res, 403, { error: 'Mojang could not verify this session' });
      const profile = await r.json();
      const uuid = normUuid(profile.id);
      if (!isUuid(uuid)) return send(res, 403, { error: 'bad profile' });
      if (cape === null || cape === '') {
        delete selections[uuid];
      } else {
        if (!available(uuid).some((c) => c.id === cape)) return send(res, 403, { error: 'you do not have this cape' });
        selections[uuid] = { cape, name: profile.name, at: new Date().toISOString() };
      }
      saveSelections();
      return send(res, 200, { ok: true, uuid, cape: selections[uuid]?.cape || null });
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
