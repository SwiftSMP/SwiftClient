'use strict';
// Discord Rich Presence over Discord's local IPC pipe (no dependency).
// Shows "Playing SwiftClient — Playing on swiftsmp.net" with the elapsed time.

const net = require('net');

const OP_HANDSHAKE = 0;
const OP_FRAME = 1;
const OP_CLOSE = 2;
const OP_PING = 3;
const OP_PONG = 4;

function encode(op, payload) {
  const json = Buffer.from(JSON.stringify(payload));
  const header = Buffer.alloc(8);
  header.writeInt32LE(op, 0);
  header.writeInt32LE(json.length, 4);
  return Buffer.concat([header, json]);
}

/** "play.swiftsmp.net:25565" -> "swiftsmp.net" */
function prettyServer(address) {
  if (!address) return null;
  let host = String(address).trim().toLowerCase().replace(/:\d+$/, '');
  host = host.replace(/^(play|mc|join|server)\./, '');
  return host;
}

/** Maps the game state to a Discord activity. */
function activityFor({ phase, server, since }, { showServer = true, buttonUrl } = {}) {
  const a = {
    assets: { large_image: 'logo', large_text: 'SwiftClient' },
    timestamps: since ? { start: Math.floor(since / 1000) } : undefined,
    instance: false,
  };
  if (phase === 'launcher') a.details = 'In the launcher';
  else if (phase === 'starting') a.details = 'Starting Minecraft';
  else if (phase === 'menu') a.details = 'In the menus';
  else if (phase === 'singleplayer') a.details = 'Playing Singleplayer';
  else if (phase === 'multiplayer') {
    const host = prettyServer(server);
    a.details = host && showServer ? `Playing on ${host}` : 'Playing Multiplayer';
  }
  if (buttonUrl) a.buttons = [{ label: 'Play on SwiftSMP', url: buttonUrl }];
  return a;
}

class DiscordPresence {
  constructor(clientId) {
    this.clientId = clientId;
    this.socket = null;
    this.ready = false;
    this.activity = null;
    this.buffer = Buffer.alloc(0);
    this.retry = null;
    this.enabled = false;
  }

  start() {
    if (!this.clientId || this.enabled) return;
    this.enabled = true;
    this.connect(0);
  }

  stop() {
    this.enabled = false;
    clearTimeout(this.retry);
    if (this.socket) {
      try { this.socket.write(encode(OP_CLOSE, {})); } catch { /* ignore */ }
      this.socket.destroy();
    }
    this.socket = null;
    this.ready = false;
  }

  connect(index) {
    if (!this.enabled) return;
    if (index > 9) {
      // Discord not running: check again later.
      this.retry = setTimeout(() => this.connect(0), 20000);
      return;
    }
    const sock = net.createConnection(`\\\\?\\pipe\\discord-ipc-${index}`);
    sock.once('error', () => { sock.destroy(); if (!this.ready) this.connect(index + 1); });
    sock.once('connect', () => {
      this.socket = sock;
      sock.on('data', (d) => this.onData(d));
      sock.on('close', () => {
        this.socket = null;
        this.ready = false;
        if (this.enabled) this.retry = setTimeout(() => this.connect(0), 15000);
      });
      sock.write(encode(OP_HANDSHAKE, { v: 1, client_id: this.clientId }));
    });
  }

  onData(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    while (this.buffer.length >= 8) {
      const op = this.buffer.readInt32LE(0);
      const len = this.buffer.readInt32LE(4);
      if (this.buffer.length < 8 + len) return;
      let msg = null;
      try { msg = JSON.parse(this.buffer.subarray(8, 8 + len).toString()); } catch { /* ignore */ }
      this.buffer = this.buffer.subarray(8 + len);
      if (op === OP_PING) this.socket.write(encode(OP_PONG, msg));
      else if (op === OP_FRAME && msg && msg.evt === 'READY') {
        this.ready = true;
        this.flush();
      } else if (op === OP_CLOSE) {
        this.socket && this.socket.destroy();
      }
    }
  }

  set(activity) {
    this.activity = activity;
    this.flush();
  }

  flush() {
    if (!this.ready || !this.socket) return;
    this.socket.write(encode(OP_FRAME, {
      cmd: 'SET_ACTIVITY',
      args: { pid: process.pid, activity: this.activity || undefined },
      nonce: `${Date.now()}-${Math.random()}`,
    }));
  }
}

// ---- reading game state from Minecraft's output ---------------------------------

const MARKER = /\[SwiftClient:presence\] (\{.*\})/;
// Vanilla fallback for versions without the SwiftClient mod.
const CONNECTING = /Connecting to ([^\s,]+), (\d+)/;
const INTEGRATED = /Starting integrated minecraft server/;

/** Parses one chunk of game output; returns {phase, server} or null if nothing relevant. */
function parseGameOutput(text) {
  let result = null;
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(MARKER);
    if (m) {
      try {
        const j = JSON.parse(m[1]);
        result = { phase: j.state, server: j.server || null };
      } catch { /* ignore */ }
      continue;
    }
    const c = line.match(CONNECTING);
    if (c) { result = { phase: 'multiplayer', server: c[2] === '25565' ? c[1] : `${c[1]}:${c[2]}` }; continue; }
    if (INTEGRATED.test(line)) result = { phase: 'singleplayer', server: null };
  }
  return result;
}

module.exports = { DiscordPresence, activityFor, parseGameOutput, prettyServer };
