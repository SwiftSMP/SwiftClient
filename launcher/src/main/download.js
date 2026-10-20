'use strict';
// HTTP helpers and a parallel, hash-verifying file downloader.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

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

async function isValid(file, { sha1, sha512, size }) {
  let st;
  try { st = fs.statSync(file); } catch { return false; }
  if (size != null && st.size !== size) return false;
  if (sha512) return (await hashFile(file, 'sha512')) === sha512;
  if (sha1) return (await hashFile(file, 'sha1')) === sha1;
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
}

module.exports = { fetchJson, fetchJsonCached, downloadAll, hashFile, isValid, USER_AGENT };
