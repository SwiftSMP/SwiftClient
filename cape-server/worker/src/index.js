// SwiftClient cape server as a Cloudflare Worker (same API as ../server.js).
//
//   GET  /capes                      catalog [{id, name, description, public}]
//   GET  /capes/<id>.png             cape texture (64x32), served from ../capes via ASSETS
//   GET  /players/<uuid>.json        {cape, url} for a player, 404 if none
//   GET  /players/<uuid>/available   capes this player may pick
//   GET  /stats                      {users, online} SwiftClient installs / online launchers
//   POST /stats/heartbeat {id,version}  records a running launcher, returns the same counts
//   POST /auth/challenge             {serverId} one-time challenge
//   POST /select {name, serverId, cape, accessToken?}
//        Identity is proved with Mojang's session join/hasJoined handshake first. Some
//        networks make that check fail (Mojang ties the session to the player's IP), so the
//        launcher may retry with its Minecraft access token, which we verify directly with
//        api.minecraftservices.com and never store or log.
//
// KV (binding CAPES): "sel:<uuid>" selections, "ch:<serverId>" challenges (2 min TTL),
// "inst:<id>" installs seen, "on:<id>" presence (25 min TTL),
// "grants" = {"<uuid>": ["staff", ...]} for non-public capes.

import catalog from '../../capes/catalog.json' with { type: 'json' };

const SESSION_SERVER_DEFAULT = 'https://sessionserver.mojang.com';
const PROFILE_URL = 'https://api.minecraftservices.com/minecraft/profile';
const USER_AGENT = 'SwiftClient-Capes/1.1 (+https://swiftsmp.com)';
const PRESENCE_TTL = 25 * 60;

const normUuid = (u) => String(u || '').toLowerCase().replace(/-/g, '');
const isUuid = (u) => /^[0-9a-f]{32}$/.test(u);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const sha256Hex = async (text) => {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
};

function send(status, body, headers = {}) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-cache', ...headers },
  });
}

async function available(env, uuid) {
  const grants = (await env.CAPES.get('grants', 'json')) || {};
  const granted = new Set(grants[uuid] || []);
  return catalog.filter((c) => c.public || granted.has(c.id));
}

async function selection(env, uuid) {
  const sel = await env.CAPES.get(`sel:${uuid}`, 'json');
  return sel && catalog.some((c) => c.id === sel.cape) ? sel : null;
}

async function readBody(request) {
  const text = await request.text();
  if (text.length > 8192) throw new Error('too large');
  try { return JSON.parse(text || '{}'); } catch { throw new Error('bad json'); }
}

// ---- identity ---------------------------------------------------------------

/** Mojang's own check: the launcher called session/minecraft/join with this serverId. */
async function handshakeProfile(sessionServer, name, serverId) {
  const query = new URLSearchParams({ username: name, serverId });
  let lastStatus = 0;
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const r = await fetch(`${sessionServer}/session/minecraft/hasJoined?${query}`, {
        headers: { 'User-Agent': USER_AGENT, Accept: 'application/json' },
      });
      lastStatus = r.status;
      if (r.status === 200) {
        const profile = await r.json();
        if (isUuid(normUuid(profile.id))) return { profile };
      }
    } catch (e) {
      lastStatus = -1;
    }
    if (attempt === 0) await sleep(250);
  }
  return { profile: null, status: lastStatus };
}

/** Fallback proof: Mojang confirms the token belongs to a real Minecraft account. */
async function tokenProfile(accessToken) {
  if (typeof accessToken !== 'string' || !/^[A-Za-z0-9._~+/=-]{20,4096}$/.test(accessToken)) {
    return { profile: null, status: 0, note: accessToken ? 'bad-format' : 'missing' };
  }
  try {
    const r = await fetch(PROFILE_URL, {
      headers: { Authorization: `Bearer ${accessToken}`, 'User-Agent': USER_AGENT, Accept: 'application/json' },
    });
    if (r.status !== 200) return { profile: null, status: r.status };
    const profile = await r.json();
    return isUuid(normUuid(profile.id)) ? { profile } : { profile: null, status: -2 };
  } catch {
    return { profile: null, status: -1 };
  }
}

async function verifyPlayer(env, { name, serverId, accessToken }) {
  const sessionServer = env.SESSION_SERVER || SESSION_SERVER_DEFAULT;
  const handshake = await handshakeProfile(sessionServer, name, serverId);
  if (handshake.profile) return { uuid: normUuid(handshake.profile.id), name: String(handshake.profile.name || name) };
  const token = await tokenProfile(accessToken);
  if (token.profile) return { uuid: normUuid(token.profile.id), name: String(token.profile.name || name) };
  // Surface why, so a failed selection can be diagnosed without Worker logs.
  return {
    failed: `hasJoined=${handshake.status}${token.note ? `(${token.note})` : ` token=${token.status}`}`,
  };
}

// ---- stats ------------------------------------------------------------------

let userCount = { at: 0, value: 0 };

async function countKeys(env, prefix, limit = 1000) {
  let cursor;
  let count = 0;
  do {
    const page = await env.CAPES.list({ prefix, limit: 1000, ...(cursor ? { cursor } : {}) });
    count += page.keys.length;
    cursor = page.list_complete ? null : page.cursor;
  } while (cursor && count < limit);
  return count;
}

async function stats(env) {
  if (Date.now() - userCount.at > 5 * 60_000) {
    userCount = { at: Date.now(), value: await countKeys(env, 'inst:').catch(() => userCount.value) };
  }
  const online = await countKeys(env, 'on:').catch(() => 0);
  return { users: userCount.value, online };
}

async function heartbeat(env, { id, version }) {
  const install = normUuid(id);
  if (!isUuid(install)) return null;
  const [knownInstall, knownPresence] = await Promise.all([
    env.CAPES.get(`inst:${install}`),
    env.CAPES.get(`on:${install}`),
  ]);
  // Count before writing: KV listings lag behind a fresh put, so a client would otherwise
  // be told it is the only offline user.
  const before = await stats(env);
  if (!knownInstall) {
    await env.CAPES.put(`inst:${install}`, JSON.stringify({ first: new Date().toISOString(), version: String(version || '').slice(0, 32) }));
    userCount = { at: 0, value: userCount.value + 1 };
  }
  if (!knownPresence) await env.CAPES.put(`on:${install}`, '1', { expirationTtl: PRESENCE_TTL });
  return {
    users: before.users + (knownInstall ? 0 : 1),
    online: before.online + (knownPresence ? 0 : 1),
  };
}

// ---- routing ----------------------------------------------------------------

export async function handle(request, env) {
  const url = new URL(request.url);
  const p = url.pathname.replace(/\/+$/, '') || '/';

  if (request.method === 'GET') {
    if (p === '/' || p === '/health') return send(200, { ok: true, capes: catalog.length, version: 2 });
    if (p === '/capes') return send(200, catalog.map(({ id, name, description, public: pub }) => ({ id, name, description, public: !!pub })));
    if (p === '/stats') return send(200, await stats(env), { 'Cache-Control': 'public, max-age=60' });
    let m = p.match(/^\/capes\/([a-z0-9_-]+)\.png$/);
    if (m) {
      if (!catalog.some((c) => c.id === m[1])) return send(404, { error: 'unknown cape' });
      const png = await env.ASSETS.fetch(new URL(`/${m[1]}.png`, url));
      return new Response(png.body, {
        status: png.status,
        headers: { 'Content-Type': 'image/png', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'public, max-age=3600' },
      });
    }
    m = p.match(/^\/players\/([0-9a-fA-F-]{32,36})\.json$/);
    if (m) {
      const sel = await selection(env, normUuid(m[1]));
      if (!sel) return send(404, { error: 'no cape' });
      return send(200, { cape: sel.cape, url: `/capes/${sel.cape}.png` }, { 'Cache-Control': 'public, max-age=60' });
    }
    m = p.match(/^\/players\/([0-9a-fA-F-]{32,36})\/available$/);
    if (m) {
      const uuid = normUuid(m[1]);
      const [sel, caps] = await Promise.all([selection(env, uuid), available(env, uuid)]);
      return send(200, { selected: sel ? sel.cape : null, capes: caps.map((c) => c.id) });
    }
    return send(404, { error: 'not found' });
  }

  if (request.method === 'POST') {
    if (p === '/auth/challenge') {
      const bytes = crypto.getRandomValues(new Uint8Array(16));
      const serverId = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
      await env.CAPES.put(`ch:${serverId}`, '1', { expirationTtl: 120 });
      return send(200, { serverId });
    }
    if (p === '/stats/heartbeat') {
      let body;
      try { body = await readBody(request); } catch (e) { return send(400, { error: e.message }); }
      const result = await heartbeat(env, body);
      return result ? send(200, result) : send(400, { error: 'bad install id' });
    }
    if (p === '/redeem') {
      let body;
      try { body = await readBody(request); } catch (e) { return send(400, { error: e.message }); }
      const { name, serverId, code, accessToken } = body;
      if (!/^[0-9a-f]{32}$/.test(serverId || '') || !(await env.CAPES.get(`ch:${serverId}`))) {
        return send(400, { error: 'unknown or expired challenge' });
      }
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name || '')) return send(400, { error: 'bad name' });
      if (typeof code !== 'string' || !/^[A-Za-z0-9-]{6,64}$/.test(code)) return send(400, { error: 'bad code' });

      const verified = await verifyPlayer(env, { name, serverId, accessToken });
      if (verified.failed) return send(403, { error: 'Mojang could not verify this session', verify: 'failed', reason: verified.failed });
      await env.CAPES.delete(`ch:${serverId}`);

      const hash = await sha256Hex(code.toUpperCase());
      const codes = (await env.CAPES.get('codes', 'json')) || {};
      const entry = codes[hash];
      if (!entry) return send(404, { error: 'That code is not valid.' });
      const redeemed = entry.redeemed || [];
      if (entry.uses && redeemed.length >= entry.uses) return send(409, { error: 'That code has already been used.' });
      if (redeemed.includes(verified.uuid)) return send(409, { error: 'You already have this cape.' });

      const grants = (await env.CAPES.get('grants', 'json')) || {};
      grants[verified.uuid] = [...new Set([...(grants[verified.uuid] || []), entry.cape])];
      entry.redeemed = [...redeemed, verified.uuid];
      await env.CAPES.put('grants', JSON.stringify(grants));
      await env.CAPES.put('codes', JSON.stringify(codes));
      return send(200, { ok: true, uuid: verified.uuid, cape: entry.cape });
    }
    if (p === '/select') {
      let body;
      try { body = await readBody(request); } catch (e) { return send(400, { error: e.message }); }
      const { name, serverId, cape, accessToken } = body;
      if (!/^[0-9a-f]{32}$/.test(serverId || '') || !(await env.CAPES.get(`ch:${serverId}`))) {
        return send(400, { error: 'unknown or expired challenge' });
      }
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name || '')) return send(400, { error: 'bad name' });

      const verified = await verifyPlayer(env, { name, serverId, accessToken });
      // Keep the challenge until it is actually used: the launcher retries it with its token.
      if (verified.failed) return send(403, { error: 'Mojang could not verify this session', verify: 'failed', reason: verified.failed });
      const player = verified;
      await env.CAPES.delete(`ch:${serverId}`);

      if (cape === null || cape === '') {
        await env.CAPES.delete(`sel:${player.uuid}`);
        return send(200, { ok: true, uuid: player.uuid, cape: null });
      }
      if (!(await available(env, player.uuid)).some((c) => c.id === cape)) return send(403, { error: 'you do not have this cape' });
      await env.CAPES.put(`sel:${player.uuid}`, JSON.stringify({ cape, name: player.name, at: new Date().toISOString() }));
      return send(200, { ok: true, uuid: player.uuid, cape });
    }
    return send(404, { error: 'not found' });
  }

  if (request.method === 'OPTIONS') return send(204, null, { 'Access-Control-Allow-Methods': 'GET, POST', 'Access-Control-Allow-Headers': 'Content-Type' });
  return send(405, { error: 'method not allowed' });
}

export default {
  async fetch(request, env) {
    try {
      return await handle(request, env);
    } catch (e) {
      console.error(e);
      return send(500, { error: 'internal error' });
    }
  },
};
