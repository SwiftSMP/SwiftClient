'use strict';
// HTTP helpers and a parallel, hash-verifying file downloader.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { paths } = require('./config');

const USER_AGENT = 'SwiftClient-Launcher (swiftsmp.com)';

async function fetchJson(url, opts = {}) {
  const res = await fetch(url, {
    ...opts,
    headers: { 'User-Agent': USER_AGENT, Accept: 'application/json', ...(opts.headers || {}) },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    const err = new Error(`${opts.method || 'GET'} ${url} -> HTTP ${res.status} ${body.slice(0, 300)}`);
    err.status = res.status;
    err.body = body;
    throw err;
  }
  if (res.status === 204) return null;
  return res.json();
}

// Cached JSON fetch: uses the network when possible, falls back to the cached copy offline.
async function fetchJsonCached(url, cacheFile, maxAgeMs = 0) {
  try {
    if (maxAgeMs > 0) {
      const st = fs.statSync(cacheFile);
      if (Date.now() - st.mtimeMs < maxAgeMs) return JSON.parse(fs.readFileSync(cacheFile, 'utf8'));
    }
  } catch { /* no cache yet */ }
  try {
    const data = await fetchJson(url);
    fs.mkdirSync(path.dirname(cacheFile), { recursive: true });
    fs.writeFileSync(cacheFile, JSON.stringify(data));
    return data;
  } catch (e) {
    if (fs.existsSync(cacheFile)) return JSON.parse(fs.readFileSync(cacheFile, 'utf8'));
    throw e;
  }
}

function hashFile(file, algo = 'sha1') {
  return new Promise((resolve, reject) => {
    const h = crypto.createHash(algo);
    fs.createReadStream(file)
      .on('data', (d) => h.update(d))
      .on('error', reject)
      .on('end', () => resolve(h.digest('hex')));
  });
}

// Re-hashing every library and asset on each launch costs seconds. Once a file has been
// verified, its size and mtime prove it has not changed, so the hash can be skipped.
const VERIFIED_CACHE = path.join(paths.cache, 'verified.json');
let verifiedFiles = null;
let verifiedDirty = false;
let verifiedTimer = null;

function loadVerified() {
  if (verifiedFiles) return verifiedFiles;
  try {
    verifiedFiles = new Map(Object.entries(JSON.parse(fs.readFileSync(VERIFIED_CACHE, 'utf8'))));
  } catch {
    verifiedFiles = new Map();
  }
  return verifiedFiles;
}

function markVerified(file, sha, stat) {
  loadVerified().set(path.resolve(file), { sha, size: stat.size, mtime: stat.mtimeMs });
  verifiedDirty = true;
  if (!verifiedTimer) {
    verifiedTimer = setTimeout(() => {
      verifiedTimer = null;
      saveVerified();
    }, 1000);
    if (verifiedTimer.unref) verifiedTimer.unref();
  }
}

function saveVerified() {
  if (!verifiedDirty || !verifiedFiles) return;
  verifiedDirty = false;
  try {
    fs.mkdirSync(path.dirname(VERIFIED_CACHE), { recursive: true });
    const map = verifiedFiles;
    // Drop entries whose files no longer exist so the cache cannot grow forever.
    for (const key of map.keys()) if (!fs.existsSync(key)) map.delete(key);
    fs.writeFileSync(VERIFIED_CACHE, JSON.stringify(Object.fromEntries(map)));
  } catch { /* cache is an optimisation: never fail a launch over it */ }
}

async function isValid(file, { sha1, sha512, size }) {
  let st;
  try { st = fs.statSync(file); } catch { return false; }
  if (size != null && st.size !== size) return false;
  const expected = sha512 || sha1 || '';
  if (expected) {
    const known = loadVerified().get(path.resolve(file));
    if (known && known.sha === expected && known.size === st.size && known.mtime === st.mtimeMs) return true;
    const actual = await hashFile(file, sha512 ? 'sha512' : 'sha1');
    if (actual !== expected) return false;
    markVerified(file, expected, st);
    return true;
  }
  return size != null || st.size > 0;
}

async function downloadOne(task, attempt = 1) {
  const { url, file } = task;
  const tmp = `${file}.part`;
  try {
    // A stalled connection must not hang the whole install: give each attempt a deadline.
    const timeoutMs = 30000 + Math.ceil((task.size || 0) / 50); // 30 s plus ~50 KB/s for big files
    const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT }, signal: AbortSignal.timeout(timeoutMs) });
    if (!res.ok) throw new Error(`GET ${url} -> HTTP ${res.status}`);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    const buf = Buffer.from(await res.arrayBuffer());
    fs.writeFileSync(tmp, buf);
    if (!(await isValid(tmp, task))) throw new Error(`Checksum mismatch for ${url}`);
    fs.renameSync(tmp, file);
    markVerified(file, task.sha512 || task.sha1 || '', fs.statSync(file));
    return buf.length;
  } catch (e) {
    try { fs.unlinkSync(tmp); } catch { /* ignore */ }
    if (attempt < 3) return downloadOne(task, attempt + 1);
    throw e;
  }
}

/**
 * Download tasks [{url, file, sha1?, sha512?, size?}] with limited concurrency,
 * skipping files that already exist and verify. onProgress(done, total, label).
 */
async function downloadAll(tasks, { concurrency = 16, onProgress = () => {}, label = 'Downloading' } = {}) {
  // De-duplicate by destination.
  const unique = [...new Map(tasks.map((t) => [path.resolve(t.file), t])).values()];
  let done = 0;
  const total = unique.length;
  const queue = unique.slice();
  onProgress(0, total, label);
  async function worker() {
    while (queue.length) {
      const task = queue.shift();
      if (!(await isValid(task.file, task))) await downloadOne(task);
      done++;
      if (done % 8 === 0 || done === total) onProgress(done, total, label);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, total) }, worker));
  saveVerified();
}

module.exports = { fetchJson, fetchJsonCached, downloadAll, hashFile, isValid, saveVerified, USER_AGENT };
