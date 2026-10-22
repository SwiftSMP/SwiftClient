'use strict';
// Minecraft Server List Ping (the "multiplayer" screen protocol): tells the launcher how
// many players are on a server without any third-party status service.

const net = require('net');
const dns = require('dns').promises;
const { performance } = require('perf_hooks');

const DEFAULT_PORT = 25565;
const PROTOCOL = 772; // 26.x; vanilla servers accept any value for a status request.

function splitAddress(address, defaultPort = DEFAULT_PORT) {
  const value = String(address || '').trim();
  if (!value) throw new Error('No server address.');
  const v6 = value.match(/^\[([^\]]+)\](?::(\d+))?$/);
  if (v6) return { host: v6[1], port: Number(v6[2] || defaultPort) };
  const parts = value.split(':');
  if (parts.length === 2 && /^\d+$/.test(parts[1])) return { host: parts[0], port: Number(parts[1]) };
  return { host: value, port: defaultPort };
}

/** Minecraft clients look up _minecraft._tcp.<host> first; a failed lookup is not an error. */
async function resolveSrv(host, timeoutMs = 1500) {
  if (!host) return { host, port: DEFAULT_PORT };
  const lookup = dns.resolveSrv(`_minecraft._tcp.${host}`).then((records) => {
    if (!records || !records.length) return { host, port: DEFAULT_PORT };
    const best = records.slice().sort((a, b) => (a.priority - b.priority) || (b.weight - a.weight))[0];
    return { host: best.name, port: best.port };
  });
  const timeout = new Promise((resolve) => setTimeout(() => resolve({ host, port: DEFAULT_PORT }), timeoutMs).unref());
  try {
    return await Promise.race([lookup, timeout]);
  } catch {
    return { host, port: DEFAULT_PORT };
  }
}

function varInt(value) {
  let n = value >>> 0;
  const bytes = [];
  do {
    let b = n & 0x7f;
    n >>>= 7;
    if (n !== 0) b |= 0x80;
    bytes.push(b);
  } while (n !== 0);
  return Buffer.from(bytes);
}

function readVarInt(buf, offset) {
  let value = 0;
  let shift = 0;
  for (let i = 0; i < 5; i++) {
    if (offset + i >= buf.length) return null;
    const byte = buf[offset + i];
    value |= (byte & 0x7f) << shift;
    if ((byte & 0x80) === 0) return { value: value | 0, size: i + 1 };
    shift += 7;
  }
  return null;
}

function stringField(str) {
  const body = Buffer.from(String(str), 'utf8');
  return Buffer.concat([varInt(body.length), body]);
}

function packet(id, ...fields) {
  const body = Buffer.concat([varInt(id), ...fields]);
  return Buffer.concat([varInt(body.length), body]);
}

function handshake(host, port) {
  const portField = Buffer.alloc(2);
  portField.writeUInt16BE(port);
  return packet(0, varInt(PROTOCOL), stringField(host), portField, varInt(1));
}

/** Chat components can be a string, {text, extra} or a list; flatten to plain text. */
function flattenMotd(description) {
  if (description == null) return '';
  if (typeof description === 'string') return description;
  if (Array.isArray(description)) return description.map(flattenMotd).join('');
  let text = '';
  if (typeof description.text === 'string') text += description.text;
  if (typeof description.translate === 'string' && !description.text) text += description.translate;
  if (description.extra) text += flattenMotd(description.extra);
  return text;
}

async function pingServer(address, { timeoutMs = 3500, srv = true } = {}) {
  const parsed = splitAddress(address);
  const target = srv ? await resolveSrv(parsed.host) : { host: parsed.host, port: parsed.port };
  const host = parsed.host; // the handshake must carry the name the player typed
  const port = target.port || parsed.port;
  const resolved = target.host || host;

  return new Promise((resolve, reject) => {
    let settled = false;
    let buffered = Buffer.alloc(0);
    const socket = net.createConnection({ host: resolved, port });
    const started = performance.now();

    const finish = (err, value) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      socket.destroy();
      if (err) reject(err); else resolve(value);
    };
    const timer = setTimeout(() => finish(new Error(`Server did not answer within ${Math.round(timeoutMs / 1000)} s.`)), timeoutMs);

    socket.on('connect', () => {
      socket.write(Buffer.concat([handshake(host, port), packet(0)]));
    });
    socket.on('error', (e) => {
      const reason = e.code === 'ECONNREFUSED' ? 'server is offline (connection refused)'
        : e.code === 'ENOTFOUND' ? 'address could not be found'
          : e.message;
      finish(new Error(reason));
    });
    socket.on('close', () => finish(new Error('Server closed the connection.')));
    socket.on('data', (chunk) => {
      buffered = Buffer.concat([buffered, chunk]);
      const length = readVarInt(buffered, 0);
      if (!length) return;
      if (buffered.length < length.size + length.value) return;
      const start = length.size;
      const id = readVarInt(buffered, start);
      if (!id) return finish(new Error('Server sent an unexpected response.'));
      if (id.value !== 0) return finish(new Error('Server sent an unexpected response.'));
      const strLen = readVarInt(buffered, start + id.size);
      if (!strLen) return finish(new Error('Server sent an unexpected response.'));
      const jsonStart = start + id.size + strLen.size;
      const text = buffered.slice(jsonStart, jsonStart + strLen.value).toString('utf8');
      let status;
      try { status = JSON.parse(text); } catch { return finish(new Error('Server sent an unreadable status.')); }
      finish(null, {
        version: { name: status.version && status.version.name, protocol: status.version && status.version.protocol },
        players: {
          online: Number(status.players && status.players.online) || 0,
          max: Number(status.players && status.players.max) || 0,
          sample: ((status.players && status.players.sample) || []).map((p) => ({ name: p.name, id: p.id })),
        },
        motd: flattenMotd(status.description).trim(),
        favicon: status.favicon || null,
        latencyMs: Math.round(performance.now() - started),
      });
    });
  });
}

module.exports = { splitAddress, resolveSrv, pingServer, flattenMotd };
