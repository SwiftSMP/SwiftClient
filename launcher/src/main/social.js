'use strict';
// Friends and direct messages, through the cape server (cape-server/worker). Every call
// proves the player the same way capes.js does: a fresh challenge, Mojang's session join,
// and the access token as a fallback when Mojang will not confirm the session by IP.

const { fetchJson } = require('./download');

const SESSION_JOIN = 'https://sessionserver.mojang.com/session/minecraft/join';

function serverError(e) {
  try {
    const body = JSON.parse(e.body || '');
    return typeof body.error === 'string' ? body.error : '';
  } catch {
    return '';
  }
}

function friendly(e) {
  const msg = serverError(e);
  if (msg) return new Error(msg);
  if (e.status === 429) return new Error('The server is busy. Please try again in a minute.');
  if (e.status >= 500) return new Error('The server hit an error. Please try again shortly.');
  if (!e.status) return new Error('Could not reach the server. Check your internet connection and try again.');
  return e;
}

class Social {
  constructor(baseUrl) {
    this.base = (baseUrl || '').replace(/\/+$/, '');
  }

  enabled() { return !!this.base; }

  async challenge() {
    const { serverId } = await fetchJson(`${this.base}/auth/challenge`, { method: 'POST' });
    return serverId;
  }

  async joinSession(session, serverId) {
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
      throw new Error(`Could not reach Mojang to confirm the session. Try again in a moment.`);
    }
  }

  /** One signed social action; retries with the access token if Mojang's IP check refused. */
  async call(session, action, extra = {}) {
    if (!this.base) throw new Error('Friends and chat are not configured for this build.');
    let serverId;
    try {
      serverId = await this.challenge();
    } catch (e) {
      throw friendly(e);
    }
    await this.joinSession(session, serverId);
    const body = { name: session.name, serverId, ...extra };
    const post = (b) => fetchJson(`${this.base}/social/${action}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(b),
    });
    try {
      return await post(body);
    } catch (e) {
      if (e.status !== 403 || !/could not verify/i.test(serverError(e))) throw friendly(e);
      try {
        return await post({ ...body, accessToken: session.accessToken });
      } catch (retry) {
        throw friendly(retry);
      }
    }
  }

  state(session) { return this.call(session, 'state'); }
  request(session, target) { return this.call(session, 'request', { target }); }
  accept(session, from) { return this.call(session, 'accept', { from }); }
  decline(session, from) { return this.call(session, 'decline', { from }); }
  remove(session, friend) { return this.call(session, 'remove', { friend }); }
  messages(session, friend) { return this.call(session, 'messages', { friend }); }
  send(session, friend, text) { return this.call(session, 'send', { friend, text }); }
}

module.exports = { Social };
