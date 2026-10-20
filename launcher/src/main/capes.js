'use strict';
// Talks to the SwiftClient cape server (cape-server/).

const { fetchJson } = require('./download');

class Capes {
  constructor(baseUrl) {
    this.base = (baseUrl || '').replace(/\/+$/, '');
  }

  enabled() { return !!this.base; }

  async catalog(uuid) {
    if (!this.base) return { enabled: false, capes: [], selected: null };
    const [capes, mine] = await Promise.all([
      fetchJson(`${this.base}/capes`),
      uuid ? fetchJson(`${this.base}/players/${uuid}/available`) : { capes: [], selected: null },
    ]);
    const owned = new Set(mine.capes);
    return {
      enabled: true,
      selected: mine.selected,
      capes: capes.map((c) => ({ ...c, owned: c.public || owned.has(c.id), image: `${this.base}/capes/${c.id}.png` })),
    };
  }

  /** Select a cape (or null to remove), proving identity with Mojang's join/hasJoined handshake. */
  async select(session, capeId) {
    if (!this.base) throw new Error('Shared capes are not configured.');
    const { serverId } = await fetchJson(`${this.base}/auth/challenge`, { method: 'POST' });
    await fetchJson('https://sessionserver.mojang.com/session/minecraft/join', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ accessToken: session.accessToken, selectedProfile: session.uuid, serverId }),
    });
    return fetchJson(`${this.base}/select`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: session.name, serverId, cape: capeId }),
    });
  }
}

module.exports = { Capes };
