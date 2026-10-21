// SwiftClient cape server as a Cloudflare Worker (same API as ../server.js).
//
//   GET  /capes                      catalog [{id, name, description, public}]
//   GET  /capes/<id>.png             cape texture (64x32), served from ../capes via ASSETS
//   GET  /players/<uuid>.json        {cape, url} for a player, 404 if none
//   GET  /players/<uuid>/available   capes this player may pick
//   POST /auth/challenge             {serverId} one-time challenge
//   POST /select {name, serverId, cape}
//        The launcher calls Mojang's session "join" with serverId, we verify with "hasJoined".
//        The player's access token never reaches this server.
//
// KV (binding CAPES): "sel:<uuid>" selections, "ch:<serverId>" challenges (2 min TTL),
// "grants" = {"<uuid>": ["staff", ...]} for non-public capes.

import catalog from '../../capes/catalog.json' with { type: 'json' };

const normUuid = (u) => String(u || '').toLowerCase().replace(/-/g, '');
const isUuid = (u) => /^[0-9a-f]{32}$/.test(u);

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
  if (text.length > 4096) throw new Error('too large');
  try { return JSON.parse(text || '{}'); } catch { throw new Error('bad json'); }
}

export async function handle(request, env) {
  const url = new URL(request.url);
  const p = url.pathname.replace(/\/+$/, '') || '/';
  const sessionServer = env.SESSION_SERVER || 'https://sessionserver.mojang.com';

  if (request.method === 'GET') {
    if (p === '/' || p === '/health') return send(200, { ok: true, capes: catalog.length });
    if (p === '/capes') return send(200, catalog.map(({ id, name, description, public: pub }) => ({ id, name, description, public: !!pub })));
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
    if (p === '/select') {
      let body;
      try { body = await readBody(request); } catch (e) { return send(400, { error: e.message }); }
      const { name, serverId, cape } = body;
      if (!/^[0-9a-f]{32}$/.test(serverId || '') || !(await env.CAPES.get(`ch:${serverId}`))) {
        return send(400, { error: 'unknown or expired challenge' });
      }
      await env.CAPES.delete(`ch:${serverId}`);
      if (!/^[A-Za-z0-9_]{1,16}$/.test(name || '')) return send(400, { error: 'bad name' });
      const r = await fetch(`${sessionServer}/session/minecraft/hasJoined?` + new URLSearchParams({ username: name, serverId }));
      if (r.status !== 200) return send(403, { error: 'Mojang could not verify this session' });
      const profile = await r.json();
      const uuid = normUuid(profile.id);
      if (!isUuid(uuid)) return send(403, { error: 'bad profile' });
      if (cape === null || cape === '') {
        await env.CAPES.delete(`sel:${uuid}`);
        return send(200, { ok: true, uuid, cape: null });
      }
      if (!(await available(env, uuid)).some((c) => c.id === cape)) return send(403, { error: 'you do not have this cape' });
      await env.CAPES.put(`sel:${uuid}`, JSON.stringify({ cape, name: profile.name, at: new Date().toISOString() }));
      return send(200, { ok: true, uuid, cape });
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
