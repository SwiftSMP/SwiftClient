'use strict';
// Talks to the SwiftClient cape server (cape-server/).

const { fetchJson } = require('./download');

const SESSION_JOIN = 'https://sessionserver.mojang.com/session/minecraft/join';

/** The cape server's own {error} text, when it sent one. */
function serverError(e) {
  try {
    const body = JSON.parse(e.body || '');
    return typeof body.error === 'string' ? body.error : '';
  } catch {
    return '';
  }
}

function friendlyCapeError(e) {
  let message;
  const msg = serverError(e);
  if (msg) message = msg;
  else if (e.status === 429) message = 'The cape server is busy. Please try again in a minute.';
  else if (e.status >= 500) message = 'The cape server hit an error. Please try again shortly.';
  else if (!e.status) message = 'Could not reach the cape server. Check your internet connection and try again.';
  if (!message) return e;
  const out = new Error(message);
  out.status = e.status;
  out.body = e.body;
  return out;
}

class Capes {
  constructor(baseUrl) {
    this.base = (baseUrl || '').replace(/\/+$/, '');
  }

  enabled() { return !!this.base; }

  async catalog(uuid) {
    if (!this.base) return { enabled: false, capes: [], selected: null };
    const [list, mine] = await Promise.allSettled([
      fetchJson(`${this.base}/capes`),
      uuid ? fetchJson(`${this.base}/players/${uuid}/available`) : Promise.resolve({ capes: [], selected: null }),
    ]);
    if (list.status === 'rejected') throw friendlyCapeError(list.reason);
    // The per-player call failing must not hide the whole catalog.
    const ownedIds = mine.status === 'fulfilled' ? (mine.value.capes || []) : [];
    const owned = new Set(ownedIds);
    return {
      enabled: true,
      selected: mine.status === 'fulfilled' ? mine.value.selected : null,
      capes: list.value.map((c) => ({ ...c, owned: c.public || owned.has(c.id), image: `${this.base}/capes/${c.id}.png` })),
    };
  }

  /**
   * Select a cape (or null to remove). Identity is proved with Mojang's join/hasJoined
   * handshake; if Mojang will not confirm that session (it ties a join to the player's IP,
   * which breaks when the launcher and the cape server see different addresses), retry with
   * the Minecraft access token, which the cape server verifies with Mojang and never stores.
   */
  async select(session, capeId) {
    if (!this.base) throw new Error('Shared capes are not configured.');
    let serverId;
    try {
      ({ serverId } = await fetchJson(`${this.base}/auth/challenge`, { method: 'POST' }));
    } catch (e) {
      throw friendlyCapeError(e);
    }

    try {
      await fetchJson(SESSION_JOIN, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accessToken: session.accessToken, selectedProfile: session.uuid, serverId }),
      });
    } catch (e) {
      if (e.status === 401 || e.status === 403) {
        throw new Error(`Mojang rejected the session for ${session.name}. Sign out and back in, then try again.`);
      }
      throw new Error(`Could not reach Mojang to confirm the session (${e.status ? `HTTP ${e.status}` : 'network error'}). Try again in a moment.`);
    }

    const body = { name: session.name, serverId, cape: capeId };
    try {
      return await this.postSelect(body);
    } catch (e) {
      if (e.status !== 403 || !/could not verify/i.test(serverError(e))) throw friendlyCapeError(e);
      try {
        return await this.postSelect({ ...body, accessToken: session.accessToken });
      } catch (retryError) {
        if (retryError.status === 403 && /could not verify/i.test(serverError(retryError))) {
          let reason = '';
          try { reason = JSON.parse(retryError.body || '{}').reason || ''; } catch { /* not json */ }
          throw new Error(`Mojang would not confirm your Minecraft session${reason ? ` (${reason})` : ''}. Sign out and back in from the account menu, then try again.`);
        }
        throw retryError;
      }
    }
  }

  /**
   * Redeem a code for a cape (staff and OG capes). Proves the player with the same join/token
   * handshake as select, then the server adds the cape to that player's account.
   */
  async redeem(session, code) {
    if (!this.base) throw new Error('Shared capes are not configured.');
    const clean = String(code || '').trim();
    if (!clean) throw new Error('Enter a code first.');
    let serverId;
    try {
      ({ serverId } = await fetchJson(`${this.base}/auth/challenge`, { method: 'POST' }));
    } catch (e) {
      throw friendlyCapeError(e);
    }
    try {
      await fetchJson(SESSION_JOIN, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accessToken: session.accessToken, selectedProfile: session.uuid, serverId }),
      });
    } catch (e) {
      if (e.status === 401 || e.status === 403) {
        throw new Error(`Mojang rejected the session for ${session.name}. Sign out and back in, then try again.`);
      }
      throw new Error(`Could not reach Mojang to confirm the session (${e.status ? `HTTP ${e.status}` : 'network error'}). Try again in a moment.`);
    }
    const body = { name: session.name, serverId, code: clean };
    try {
      return await fetchJson(`${this.base}/redeem`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    } catch (e) {
      if (e.status !== 403 || !/could not verify/i.test(serverError(e))) throw friendlyCapeError(e);
      try {
        return await fetchJson(`${this.base}/redeem`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...body, accessToken: session.accessToken }),
        });
      } catch (retryError) {
        throw friendlyCapeError(retryError);
      }
    }
  }

  /**
   * Admin only: list or create redeem codes. Same join/token handshake as redeem; the server
   * refuses anyone who is not on its admin list.
   */
  async adminCodes(session, request) {
    if (!this.base) throw new Error('Shared capes are not configured.');
    let serverId;
    try {
      ({ serverId } = await fetchJson(`${this.base}/auth/challenge`, { method: 'POST' }));
    } catch (e) {
      throw friendlyCapeError(e);
    }
    try {
      await fetchJson(SESSION_JOIN, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accessToken: session.accessToken, selectedProfile: session.uuid, serverId }),
      });
    } catch (e) {
      if (e.status === 401 || e.status === 403) {
        throw new Error(`Mojang rejected the session for ${session.name}. Sign out and back in, then try again.`);
      }
      throw new Error(`Could not reach Mojang to confirm the session (${e.status ? `HTTP ${e.status}` : 'network error'}). Try again in a moment.`);
    }
    const body = { name: session.name, serverId, ...request };
    try {
      return await fetchJson(`${this.base}/admin/codes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    } catch (e) {
      if (e.status !== 403 || !/could not verify/i.test(serverError(e))) throw friendlyCapeError(e);
      try {
        return await fetchJson(`${this.base}/admin/codes`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ ...body, accessToken: session.accessToken }),
        });
      } catch (retryError) {
        throw friendlyCapeError(retryError);
      }
    }
  }

  async postSelect(body) {
    try {
      return await fetchJson(`${this.base}/select`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    } catch (e) {
      throw friendlyCapeError(e);
    }
  }
}

module.exports = { Capes, friendlyCapeError };
