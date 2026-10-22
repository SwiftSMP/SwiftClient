'use strict';
// Anonymous SwiftClient usage counters: how many installs exist and how many launchers are
// running right now (the cape server counts them). Only a random install id and the version
// are sent; nothing ties the id to a Minecraft account.

const fs = require('fs');
const crypto = require('crypto');
const path = require('path');
const { paths } = require('./config');
const { fetchJson } = require('./download');

const HEARTBEAT_MS = 10 * 60_000;
const installIdFile = path.join(paths.root, 'install-id');

function installId() {
  try {
    const id = fs.readFileSync(installIdFile, 'utf8').trim();
    if (/^[0-9a-f]{32}$/.test(id)) return id;
  } catch { /* first run */ }
  const id = crypto.randomBytes(16).toString('hex');
  try {
    fs.mkdirSync(paths.root, { recursive: true });
    fs.writeFileSync(installIdFile, id);
  } catch { /* read-only install: counts still work for this run */ }
  return id;
}

class Stats {
  constructor(baseUrl) {
    this.base = (baseUrl || '').replace(/\/+$/, '');
    this.timer = null;
    this.last = null;
  }

  enabled() { return !!this.base; }

  async heartbeat(version) {
    if (!this.base) return null;
    const value = await fetchJson(`${this.base}/stats/heartbeat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: installId(), version }),
    });
    this.last = value;
    return value;
  }

  /** Starts the heartbeat loop. onUpdate receives the fresh {users, online} counts. */
  start(version, { enabled = true, onUpdate = () => {} } = {}) {
    this.stop();
    if (!this.base || !enabled) return;
    const beat = () => this.heartbeat(version).then((s) => { if (s) onUpdate(s); }).catch(() => { /* offline: try next time */ });
    beat();
    this.timer = setInterval(beat, HEARTBEAT_MS);
    if (this.timer.unref) this.timer.unref();
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }
}

module.exports = { Stats, installId, HEARTBEAT_MS };
